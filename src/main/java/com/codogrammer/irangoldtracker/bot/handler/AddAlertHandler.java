package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.bot.alert.AlertDraftStore;
import com.codogrammer.irangoldtracker.bot.alert.AlertKeyboard;
import com.codogrammer.irangoldtracker.bot.alert.Draft;
import com.codogrammer.irangoldtracker.bot.alert.Step;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class AddAlertHandler {

    public static final String PICK_MARKET_PREFIX = "ALERT_MARKET:";
    public static final String PICK_ITEM_PREFIX = "ALERT_ITEM:";
    public static final String PAGE_PREFIX = "ALERT_PAGE:";
    public static final String BACK_TO_MARKETS = "ALERT_MARKETS";
    public static final String CANCEL = "ALERT_CANCEL";

    private static final Set<String> CANCEL_WORDS =
            Set.of("لغو", "بیخیال", "cancel", "/cancel");

    private final TelegramMessageSender sender;
    private final AlertService alertService;
    private final MarketPriceCache marketPriceCache;
    private final AlertDraftStore draftStore;
    private final AlertKeyboard keyboard;

    public AddAlertHandler(
            TelegramMessageSender sender,
            AlertService alertService,
            MarketPriceCache marketPriceCache,
            AlertDraftStore draftStore,
            AlertKeyboard keyboard
    ) {
        this.sender = sender;
        this.alertService = alertService;
        this.marketPriceCache = marketPriceCache;
        this.draftStore = draftStore;
        this.keyboard = keyboard;
    }

    public boolean isAwaitingInput(Long chatId) {
        return draftStore.exists(chatId);
    }

    public boolean handle(Update update) {

        User user = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        TelegramUser telegramUser = alertService.register(
                user.getId(),
                chatId,
                user.getFirstName(),
                user.getUserName()
        );

        long remaining = alertService.remainingSlots(telegramUser.getId());

        if (remaining <= 0) {
            sender.send(
                    chatId,
                    "سه هشدار داری، بیشتر از این نمیشه. اول یکی رو از مدیریت هشدار حذف کن."
            );
            return true;
        }

        draftStore.start(chatId);
        sendMarkets(chatId, remaining);

        return false;
    }

    public boolean handleInput(Update update) {

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        Optional<Draft> draftOptional = draftStore.get(chatId);

        if (draftOptional.isEmpty()) {
            return true;
        }

        if (isCancel(text)) {
            return cancel(chatId);
        }

        Draft draft = draftOptional.get();

        return switch (draft.getStep()) {

            case MARKET -> rejectMarketInput(chatId);

            case ITEM -> rejectItemInput(chatId, draft);

            case FROM -> {
                handleFromPrice(chatId, draft, text);
                yield false;
            }

            case TO -> handleToPrice(
                    chatId,
                    update.getMessage().getFrom(),
                    draft,
                    text
            );
        };
    }

    public boolean handlePickMarket(Update update, String marketName) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Optional<Draft> draftOptional = draftStore.get(chatId);

        if (draftOptional.isEmpty()) {
            return stale(chatId);
        }

        Optional<MarketCurrencies> market = findMarket(marketName);

        if (market.isEmpty()) {
            log.warn("Unknown market in callback: {}", marketName);
            return stale(chatId);
        }

        Draft draft = draftOptional.get();

        List<MarketItem> items = availableItems(userId, market.get());

        if (items.isEmpty()) {
            sender.send(
                    chatId,
                    "برای " + market.get().getPersianName()
                            + " چیزی نمونده که هشدار نداشته باشی.",
                    keyboard.markets()
            );
            return false;
        }

        draft.selectMarket(market.get(), items);

        sender.send(
                chatId,
                market.get().getPersianName()
                        + " رو انتخاب کردی، حالا کدومش؟",
                keyboard.items(draft)
        );

        return false;
    }

    public boolean handlePage(Update update, int page) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Optional<Draft> draftOptional = draftStore.get(chatId);

        if (draftOptional.isEmpty()) {
            return stale(chatId);
        }

        Draft draft = draftOptional.get();

        if (draft.getStep() != Step.ITEM) {
            return stale(chatId);
        }

        draft.changePage(page);

        sender.send(
                chatId,
                "کدومش؟",
                keyboard.items(draft)
        );

        return false;
    }

    public boolean handleBackToMarkets(Update update) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Optional<Draft> draftOptional = draftStore.get(chatId);

        if (draftOptional.isEmpty()) {
            return stale(chatId);
        }

        Draft draft = draftOptional.get();
        draft.resetToMarket();

        sendMarkets(
                chatId,
                alertService.remainingSlots(userId)
        );

        return false;
    }

    public boolean handlePick(Update update, int index) {

        User user = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Optional<Draft> draftOptional = draftStore.get(chatId);

        if (draftOptional.isEmpty()) {
            return stale(chatId);
        }

        Draft draft = draftOptional.get();

        if (!draft.canSelectItem(index)) {
            return stale(chatId);
        }

        return selectItem(
                chatId,
                user.getId(),
                draft,
                draft.getItems().get(index)
        );
    }

    public boolean handleCancel(Update update) {
        return cancel(
                update.getCallbackQuery()
                        .getMessage()
                        .getChatId()
        );
    }

    private boolean rejectMarketInput(Long chatId) {

        sender.send(
                chatId,
                "از بین همون دکمه‌های بالا یکی رو انتخاب کن.",
                keyboard.markets()
        );

        return false;
    }

    private boolean rejectItemInput(Long chatId, Draft draft) {

        sender.send(
                chatId,
                "از بین همون دکمه‌های بالا یکی رو انتخاب کن.",
                keyboard.items(draft)
        );

        return false;
    }

    private boolean selectItem(
            Long chatId,
            Long userId,
            Draft draft,
            MarketItem item
    ) {

        if (alertService.alreadyWatches(
                userId,
                draft.getMarket(),
                item.name()
        )) {

            sender.send(
                    chatId,
                    "برای «" + item.name()
                            + "» قبلاً هشدار داری. "
                            + "اول از مدیریت هشدار حذفش کن."
            );

            draftStore.remove(chatId);
            return true;
        }

        draft.selectItem(item);

        sender.send(
                chatId,
                item.name()
                        + " الان "
                        + Utils.formatPrice(item.price())
                        + unit(item)
                        + "\n\n"
                        + "کف قیمت رو بفرست "
                        + "(پایین‌تر از این خبرت میکنم)",
                keyboard.cancel()
        );

        return false;
    }

    private void handleFromPrice(
            Long chatId,
            Draft draft,
            String text
    ) {

        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(
                    chatId,
                    "❌ کف قیمت رو نفهمیدم، "
                            + "یه عدد بزرگتر از صفر بفرست.",
                    keyboard.cancel()
            );
            return;
        }

        draft.setFromPrice(price.get());
        draft.setStep(Step.TO);

        sender.send(
                chatId,
                "کف شد "
                        + Utils.formatPrice(draft.getFromPrice())
                        + "\n\nحالا سقف قیمت رو بفرست",
                keyboard.cancel()
        );
    }

    private boolean handleToPrice(
            Long chatId,
            User user,
            Draft draft,
            String text
    ) {

        Optional<BigDecimal> price = positivePrice(text);

        if (price.isEmpty()) {
            sender.send(
                    chatId,
                    "❌ سقف قیمت رو نفهمیدم، "
                            + "یه عدد بزرگتر از صفر بفرست.",
                    keyboard.cancel()
            );
            return false;
        }

        BigDecimal toPrice = price.get();

        if (toPrice.compareTo(draft.getFromPrice()) <= 0) {
            sender.send(
                    chatId,
                    "❌ سقف باید بیشتر از کف ("
                            + Utils.formatPrice(draft.getFromPrice())
                            + ") باشه.",
                    keyboard.cancel()
            );
            return false;
        }

        save(chatId, user, draft, toPrice);

        return true;
    }

    private void save(
            Long chatId,
            User user,
            Draft draft,
            BigDecimal toPrice
    ) {

        TelegramUser telegramUser = alertService.register(
                user.getId(),
                chatId,
                user.getFirstName(),
                user.getUserName()
        );

        draftStore.remove(chatId);

        if (alertService.hasReachedLimit(telegramUser.getId())) {
            sender.send(
                    chatId,
                    "سه هشدار داری، بیشتر از این نمیشه."
            );
            return;
        }

        if (alertService.alreadyWatches(
                telegramUser.getId(),
                draft.getMarket(),
                draft.getItem().name()
        )) {
            sender.send(
                    chatId,
                    "برای «" + draft.getItem().name()
                            + "» همین الان هشدار داری."
            );
            return;
        }

        Alert alert = alertService.create(
                telegramUser,
                draft.getMarket(),
                draft.getItem().name(),
                draft.getItem().symbol(),
                draft.getFromPrice(),
                toPrice
        );

        sender.send(
                chatId,
                "✅ هشدار ثبت شد\n"
                        + alert.getItemName()
                        + " : از "
                        + Utils.formatPrice(alert.getFromPrice())
                        + " تا "
                        + Utils.formatPrice(alert.getToPrice())
        );
    }

    private void sendMarkets(Long chatId, long remaining) {

        sender.send(
                chatId,
                """
                ➕ افزودن هشدار (%d جای خالی داری)
    
                اول بگو از کدوم بازار؟
                """.formatted(remaining),
                keyboard.markets()
        );
    }

    private List<MarketItem> availableItems(
            Long userId,
            MarketCurrencies market
    ) {

        return Utils.itemsOf(
                        marketPriceCache.getMarketPrices(),
                        market
                )
                .stream()
                .filter(item -> item.name() != null)
                .filter(item ->
                        !alertService.alreadyWatches(
                                userId,
                                market,
                                item.name()
                        )
                )
                .toList();
    }

    private Optional<MarketCurrencies> findMarket(String name) {

        return Arrays.stream(MarketCurrencies.values())
                .filter(market -> market.name().equals(name))
                .findFirst();
    }

    private Optional<BigDecimal> positivePrice(String text) {

        return Utils.parsePrice(text)
                .filter(price -> price.signum() > 0);
    }

    private boolean isCancel(String text) {

        return CANCEL_WORDS.contains(text.toLowerCase());
    }

    private String unit(MarketItem item) {

        return item.unit() == null
                ? ""
                : " " + item.unit();
    }

    private boolean cancel(Long chatId) {

        draftStore.remove(chatId);

        sender.send(
                chatId,
                "باشه، بیخیال هشدار شدم."
        );

        return true;
    }

    private boolean stale(Long chatId) {

        draftStore.remove(chatId);

        sender.send(
                chatId,
                "این انتخاب قدیمیه، از اول شروع کن."
        );

        return true;
    }
}