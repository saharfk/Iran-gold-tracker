package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.bot.draft.AlertDraft;
import com.codogrammer.irangoldtracker.bot.draft.AlertDraftStore;
import com.codogrammer.irangoldtracker.bot.draft.DraftKey;
import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.service.AlertService;
import com.codogrammer.irangoldtracker.service.CreateAlertCommand;
import com.codogrammer.irangoldtracker.service.CreateAlertResult;
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
import java.util.Optional;
import java.util.Set;

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

    private final TelegramMessageSender sender;
    private final AlertService alertService;
    private final MarketPriceCache marketPriceCache;
    private final AlertDraftStore drafts;

    public AddAlertHandler(
            TelegramMessageSender sender,
            AlertService alertService,
            MarketPriceCache marketPriceCache,
            AlertDraftStore drafts
    ) {
        this.sender = sender;
        this.alertService = alertService;
        this.marketPriceCache = marketPriceCache;
        this.drafts = drafts;
    }

    public boolean isAwaitingInput(Long chatId, Long userId) {
        return drafts.find(new DraftKey(chatId, userId)).isPresent();
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

        drafts.save(key(update), new AlertDraft());
        sendMarkets(chatId, remaining);

        return false;
    }

    public boolean handleInput(Update update) {

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        Optional<AlertDraft> found = drafts.find(key(update));

        if (found.isEmpty()) {
            return true;
        }

        AlertDraft draft = found.get();

        if (CANCEL_WORDS.contains(text.toLowerCase())) {
            return cancel(update);
        }

        return switch (draft.getStep()) {

            case MARKET -> {
                sender.send(chatId, "از بین همون دکمه‌های بالا یکی رو انتخاب کن.", marketKeyboard());
                yield false;
            }

            case ITEM -> {
                sender.send(chatId, "از بین همون دکمه‌های بالا یکی رو انتخاب کن.", itemKeyboard(draft));
                yield false;
            }

            case FROM -> onFromPrice(update, draft, text);

            case TO -> onToPrice(update, draft, text);
        };
    }

    public boolean handlePickMarket(Update update, String marketName) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Optional<AlertDraft> found = drafts.find(key(update));

        if (found.isEmpty()) {
            return stale(update);
        }

        Optional<MarketCurrencies> market = market(marketName);

        if (market.isEmpty()) {
            log.warn("Unknown market in alert callback: {}", marketName);
            return stale(update);
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

        AlertDraft draft = found.get();
        draft.setMarket(market.get());
        draft.setItems(items);
        draft.setPage(0);
        draft.setStep(AlertDraft.Step.ITEM);
        drafts.save(key(update), draft);

        sender.send(chatId, market.get().getPersianName() + " رو انتخاب کردی، حالا کدومش؟", itemKeyboard(draft));
        return false;
    }

    public boolean handlePage(Update update, int page) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Optional<AlertDraft> found = drafts.find(key(update));

        if (found.isEmpty() || found.get().getStep() != AlertDraft.Step.ITEM) {
            return stale(update);
        }

        AlertDraft draft = found.get();
        draft.setPage(Math.max(0, Math.min(page, lastPage(draft))));
        drafts.save(key(update), draft);

        sender.send(chatId, "کدومش؟", itemKeyboard(draft));
        return false;
    }

    public boolean handleBackToMarkets(Update update) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Optional<AlertDraft> found = drafts.find(key(update));

        if (found.isEmpty()) {
            return stale(update);
        }

        AlertDraft draft = found.get();
        draft.backToMarkets();
        drafts.save(key(update), draft);

        sendMarkets(chatId, alertService.remainingSlots(update.getCallbackQuery().getFrom().getId()));
        return false;
    }

    public boolean handlePick(Update update, int index) {

        User from = update.getCallbackQuery().getFrom();
        Optional<AlertDraft> found = drafts.find(key(update));

        if (found.isEmpty()
                || found.get().getStep() != AlertDraft.Step.ITEM
                || index < 0
                || index >= found.get().getItems().size()) {
            return stale(update);
        }

        AlertDraft draft = found.get();

        return selectItem(update, from.getId(), draft, draft.getItems().get(index));
    }

    public boolean handleCancel(Update update) {
        return cancel(update);
    }

    private DraftKey key(Update update) {

        if (update.hasCallbackQuery()) {
            return new DraftKey(
                    update.getCallbackQuery().getMessage().getChatId(),
                    update.getCallbackQuery().getFrom().getId()
            );
        }

        return new DraftKey(update.getMessage().getChatId(), update.getMessage().getFrom().getId());
    }

    private Long chatId(Update update) {
        return update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();
    }

    private boolean cancel(Update update) {

        drafts.remove(key(update));
        sender.send(chatId(update), "باشه، بیخیال هشدار شدم.");
        return true;
    }

    private boolean stale(Update update) {

        drafts.remove(key(update));
        sender.send(chatId(update), "این انتخاب قدیمیه، از اول شروع کن.");
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

    private boolean selectItem(Update update, Long userId, AlertDraft draft, MarketItem item) {

        Long chatId = chatId(update);

        if (alertService.alreadyWatches(userId, draft.getMarket(), item.name())) {
            sender.send(chatId, "برای «" + item.name() + "» قبلاً هشدار داری. اول از مدیریت هشدار حذفش کن.");
            drafts.remove(key(update));
            return true;
        }

        draft.setItem(item);
        draft.setStep(AlertDraft.Step.FROM);
        drafts.save(key(update), draft);

        sender.send(
                chatId,
                item.name() + " الان " + Utils.formatPrice(item.price()) + unit(item) + "\n\n"
                        + "کف قیمت رو بفرست (پایین‌تر از این خبرت میکنم)",
                cancelKeyboard()
        );

        return false;
    }

    private boolean onFromPrice(Update update, AlertDraft draft, String text) {

        Long chatId = chatId(update);
        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(chatId, "❌ کف قیمت رو نفهمیدم، یه عدد بزرگتر از صفر بفرست.", cancelKeyboard());
            return false;
        }

        draft.setFromPrice(price.get());
        draft.setStep(AlertDraft.Step.TO);
        drafts.save(key(update), draft);

        sender.send(
                chatId,
                "کف شد " + Utils.formatPrice(draft.getFromPrice()) + "\n\nحالا سقف قیمت رو بفرست",
                cancelKeyboard()
        );

        return false;
    }

    private boolean onToPrice(Update update, AlertDraft draft, String text) {

        Long chatId = chatId(update);
        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(chatId, "❌ سقف قیمت رو نفهمیدم، یه عدد بزرگتر از صفر بفرست.", cancelKeyboard());
            return false;
        }

        if (price.get().compareTo(draft.getFromPrice()) <= 0) {
            sender.send(
                    chatId,
                    "❌ سقف باید بیشتر از کف (" + Utils.formatPrice(draft.getFromPrice()) + ") باشه.",
                    cancelKeyboard()
            );
            return false;
        }

        return save(update, draft, price.get());
    }

    private boolean save(Update update, AlertDraft draft, BigDecimal toPrice) {

        User from = update.getMessage().getFrom();
        Long chatId = chatId(update);

        drafts.remove(key(update));

        CreateAlertResult result = alertService.create(new CreateAlertCommand(
                from.getId(),
                chatId,
                from.getFirstName(),
                from.getUserName(),
                draft.getMarket(),
                draft.getItem().name(),
                draft.getItem().symbol(),
                draft.getFromPrice(),
                toPrice
        ));

        sender.send(chatId, describe(result));
        return true;
    }

    private String describe(CreateAlertResult result) {

        return switch (result) {

            case CreateAlertResult.LimitReached reached ->
                    "%d هشدار داری، بیشتر از این نمیشه.".formatted(reached.maxAlerts());

            case CreateAlertResult.Duplicate duplicate ->
                    "برای «" + duplicate.itemName() + "» همین الان هشدار داری.";

            case CreateAlertResult.Created created -> "✅ هشدار ثبت شد\n"
                    + created.alert().getItemName()
                    + " : از " + Utils.formatPrice(created.alert().getFromPrice())
                    + " تا " + Utils.formatPrice(created.alert().getToPrice());
        };
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

    private int lastPage(AlertDraft draft) {
        return Math.max(0, (draft.getItems().size() - 1) / PAGE_SIZE);
    }

    private InlineKeyboardMarkup marketKeyboard() {

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (MarketCurrencies market : MarketCurrencies.values()) {
            rows.add(new InlineKeyboardRow(button(market.getPersianName(), PICK_MARKET_PREFIX + market.name())));
        }

        rows.add(new InlineKeyboardRow(button("✖️ لغو", CANCEL)));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup itemKeyboard(AlertDraft draft) {

        int from = draft.getPage() * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, draft.getItems().size());

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int index = from; index < to; index++) {

            MarketItem item = draft.getItems().get(index);
            rows.add(new InlineKeyboardRow(button(
                    item.name() + " (" + Utils.formatPrice(item.price()) + ")",
                    PICK_ITEM_PREFIX + index
            )));
        }

        InlineKeyboardRow navigation = new InlineKeyboardRow();

        if (draft.getPage() > 0) {
            navigation.add(button("⬅️ قبلی", PAGE_PREFIX + (draft.getPage() - 1)));
        }

        if (draft.getPage() < lastPage(draft)) {
            navigation.add(button("بعدی ➡️", PAGE_PREFIX + (draft.getPage() + 1)));
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
