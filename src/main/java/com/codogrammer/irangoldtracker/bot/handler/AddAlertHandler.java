package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.entity.TelegramUser;
import com.codogrammer.irangoldtracker.service.AlertService;
import com.codogrammer.irangoldtracker.service.MarketPriceCache;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import com.codogrammer.irangoldtracker.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walks the user through one alert at a time : market buttons, then the item buttons of that
 * market, then the low price, then the high price.
 * Every method returns {@code true} when the conversation is over and the main menu can be shown again.
 */
@Service
@Slf4j
public class AddAlertHandler {

    public static final String PICK_MARKET_PREFIX = "ALERT_MARKET:";
    public static final String PICK_ITEM_PREFIX = "ALERT_ITEM:";
    public static final String PAGE_PREFIX = "ALERT_PAGE:";
    public static final String BACK_TO_MARKETS = "ALERT_MARKETS";
    public static final String CANCEL = "ALERT_CANCEL";

    private static final int PAGE_SIZE = 8;
    private static final Set<String> CANCEL_WORDS = Set.of("لغو", "بیخیال", "cancel", "/cancel");

    private enum Step {
        MARKET, ITEM, FROM, TO
    }

    private static final class Draft {

        private Step step = Step.MARKET;
        private MarketCurrencies market;
        private List<MarketItem> items = List.of();
        private int page;
        private MarketItem item;
        private BigDecimal fromPrice;
    }

    private final TelegramMessageSender sender;
    private final AlertService alertService;
    private final MarketPriceCache marketPriceCache;
    private final Map<Long, Draft> drafts = new ConcurrentHashMap<>();

    public AddAlertHandler(
            TelegramMessageSender sender,
            AlertService alertService,
            MarketPriceCache marketPriceCache
    ) {
        this.sender = sender;
        this.alertService = alertService;
        this.marketPriceCache = marketPriceCache;
    }

    public boolean isAwaitingInput(Long chatId) {
        return drafts.containsKey(chatId);
    }

    public boolean handle(Update update) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        alertService.register(from.getId(), chatId, from.getFirstName(), from.getUserName());

        long remaining = alertService.remainingSlots(from.getId());

        if (remaining <= 0) {
            sender.send(chatId, "سه هشدار داری، بیشتر از این نمیشه. اول یکی رو از مدیریت هشدار حذف کن.");
            return true;
        }

        drafts.put(chatId, new Draft());
        sendMarkets(chatId, remaining);

        return false;
    }

    public boolean handleInput(Update update) {

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        Draft draft = drafts.get(chatId);

        if (draft == null) {
            return true;
        }

        if (CANCEL_WORDS.contains(text.toLowerCase())) {
            return cancel(chatId);
        }

        return switch (draft.step) {

            case MARKET -> {
                sender.send(chatId, "از بین همون دکمه‌های بالا یکی رو انتخاب کن.", marketKeyboard());
                yield false;
            }

            case ITEM -> {
                sender.send(chatId, "از بین همون دکمه‌های بالا یکی رو انتخاب کن.", itemKeyboard(draft));
                yield false;
            }

            case FROM -> onFromPrice(chatId, draft, text);

            case TO -> onToPrice(chatId, update.getMessage().getFrom(), draft, text);
        };
    }

    public boolean handlePickMarket(Update update, String marketName) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Draft draft = drafts.get(chatId);

        if (draft == null) {
            return stale(chatId);
        }

        Optional<MarketCurrencies> market = market(marketName);

        if (market.isEmpty()) {
            log.warn("Unknown market in alert callback: {}", marketName);
            return stale(chatId);
        }

        List<MarketItem> items = available(userId, market.get());

        if (items.isEmpty()) {
            sender.send(
                    chatId,
                    "برای " + market.get().getPersianName() + " چیزی نمونده که هشدار نداشته باشی.",
                    marketKeyboard()
            );
            return false;
        }

        draft.market = market.get();
        draft.items = items;
        draft.page = 0;
        draft.step = Step.ITEM;

        sender.send(chatId, market.get().getPersianName() + " رو انتخاب کردی، حالا کدومش؟", itemKeyboard(draft));
        return false;
    }

    public boolean handlePage(Update update, int page) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Draft draft = drafts.get(chatId);

        if (draft == null || draft.step != Step.ITEM) {
            return stale(chatId);
        }

        draft.page = Math.max(0, Math.min(page, lastPage(draft)));

        sender.send(chatId, "کدومش؟", itemKeyboard(draft));
        return false;
    }

    public boolean handleBackToMarkets(Update update) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Draft draft = drafts.get(chatId);

        if (draft == null) {
            return stale(chatId);
        }

        draft.market = null;
        draft.items = List.of();
        draft.page = 0;
        draft.item = null;
        draft.fromPrice = null;
        draft.step = Step.MARKET;

        sendMarkets(chatId, alertService.remainingSlots(update.getCallbackQuery().getFrom().getId()));
        return false;
    }

    public boolean handlePick(Update update, int index) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Draft draft = drafts.get(chatId);

        if (draft == null || draft.step != Step.ITEM || index < 0 || index >= draft.items.size()) {
            return stale(chatId);
        }

        return selectItem(chatId, from.getId(), draft, draft.items.get(index));
    }

    public boolean handleCancel(Update update) {
        return cancel(update.getCallbackQuery().getMessage().getChatId());
    }

    private boolean cancel(Long chatId) {

        drafts.remove(chatId);
        sender.send(chatId, "باشه، بیخیال هشدار شدم.");
        return true;
    }

    private boolean stale(Long chatId) {

        drafts.remove(chatId);
        sender.send(chatId, "این انتخاب قدیمیه، از اول شروع کن.");
        return true;
    }

    private void sendMarkets(Long chatId, long remaining) {

        sender.send(
                chatId,
                "➕ افزودن هشدار (%d جای خالی داری)\n\nاول بگو از کدوم بازار؟".formatted(remaining),
                marketKeyboard()
        );
    }

    private List<MarketItem> available(Long userId, MarketCurrencies market) {

        return Utils.itemsOf(marketPriceCache.getMarketPrices(), market).stream()
                .filter(item -> item.name() != null)
                .filter(item -> !alertService.alreadyWatches(userId, market, item.name()))
                .toList();
    }

    private boolean selectItem(Long chatId, Long userId, Draft draft, MarketItem item) {

        if (alertService.alreadyWatches(userId, draft.market, item.name())) {
            sender.send(chatId, "برای «" + item.name() + "» قبلاً هشدار داری. اول از مدیریت هشدار حذفش کن.");
            drafts.remove(chatId);
            return true;
        }

        draft.item = item;
        draft.step = Step.FROM;

        sender.send(
                chatId,
                item.name() + " الان " + Utils.formatPrice(item.price()) + unit(item) + "\n\n"
                        + "کف قیمت رو بفرست (پایین‌تر از این خبرت میکنم)",
                cancelKeyboard()
        );

        return false;
    }

    private boolean onFromPrice(Long chatId, Draft draft, String text) {

        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(chatId, "❌ کف قیمت رو نفهمیدم، یه عدد بزرگتر از صفر بفرست.", cancelKeyboard());
            return false;
        }

        draft.fromPrice = price.get();
        draft.step = Step.TO;

        sender.send(
                chatId,
                "کف شد " + Utils.formatPrice(draft.fromPrice) + "\n\nحالا سقف قیمت رو بفرست",
                cancelKeyboard()
        );

        return false;
    }

    private boolean onToPrice(Long chatId, User from, Draft draft, String text) {

        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(chatId, "❌ سقف قیمت رو نفهمیدم، یه عدد بزرگتر از صفر بفرست.", cancelKeyboard());
            return false;
        }

        if (price.get().compareTo(draft.fromPrice) <= 0) {
            sender.send(
                    chatId,
                    "❌ سقف باید بیشتر از کف (" + Utils.formatPrice(draft.fromPrice) + ") باشه.",
                    cancelKeyboard()
            );
            return false;
        }

        return save(chatId, from, draft, price.get());
    }

    private boolean save(Long chatId, User from, Draft draft, BigDecimal toPrice) {

        TelegramUser user = alertService.register(from.getId(), chatId, from.getFirstName(), from.getUserName());

        drafts.remove(chatId);

        if (alertService.hasReachedLimit(user.getId())) {
            sender.send(chatId, "سه هشدار داری، بیشتر از این نمیشه.");
            return true;
        }

        if (alertService.alreadyWatches(user.getId(), draft.market, draft.item.name())) {
            sender.send(chatId, "برای «" + draft.item.name() + "» همین الان هشدار داری.");
            return true;
        }

        Alert alert = alertService.create(
                user,
                draft.market,
                draft.item.name(),
                draft.item.symbol(),
                draft.fromPrice,
                toPrice
        );

        sender.send(
                chatId,
                "✅ هشدار ثبت شد\n"
                        + alert.getItemName()
                        + " : از " + Utils.formatPrice(alert.getFromPrice())
                        + " تا " + Utils.formatPrice(alert.getToPrice())
        );

        return true;
    }

    private Optional<MarketCurrencies> market(String name) {

        return Arrays.stream(MarketCurrencies.values())
                .filter(market -> market.name().equals(name))
                .findFirst();
    }

    private Optional<BigDecimal> positivePrice(String text) {
        return Utils.parsePrice(text).filter(price -> price.signum() > 0);
    }

    private String unit(MarketItem item) {
        return item.unit() == null ? "" : " " + item.unit();
    }

    private int lastPage(Draft draft) {
        return Math.max(0, (draft.items.size() - 1) / PAGE_SIZE);
    }

    private InlineKeyboardMarkup marketKeyboard() {

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (MarketCurrencies market : MarketCurrencies.values()) {
            rows.add(new InlineKeyboardRow(button(market.getPersianName(), PICK_MARKET_PREFIX + market.name())));
        }

        rows.add(new InlineKeyboardRow(button("✖️ لغو", CANCEL)));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup itemKeyboard(Draft draft) {

        int from = draft.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, draft.items.size());

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int index = from; index < to; index++) {

            MarketItem item = draft.items.get(index);
            rows.add(new InlineKeyboardRow(button(
                    item.name() + " (" + Utils.formatPrice(item.price()) + ")",
                    PICK_ITEM_PREFIX + index
            )));
        }

        InlineKeyboardRow navigation = new InlineKeyboardRow();

        if (draft.page > 0) {
            navigation.add(button("⬅️ قبلی", PAGE_PREFIX + (draft.page - 1)));
        }

        if (draft.page < lastPage(draft)) {
            navigation.add(button("بعدی ➡️", PAGE_PREFIX + (draft.page + 1)));
        }

        if (!navigation.isEmpty()) {
            rows.add(navigation);
        }

        rows.add(new InlineKeyboardRow(button("🔙 بازارها", BACK_TO_MARKETS)));
        rows.add(new InlineKeyboardRow(button("✖️ لغو", CANCEL)));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup cancelKeyboard() {

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(button("✖️ لغو", CANCEL)));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardButton button(String text, String callbackData) {

        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
