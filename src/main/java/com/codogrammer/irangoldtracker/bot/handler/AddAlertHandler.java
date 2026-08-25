package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.dto.MarketItemMatch;
import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.entity.TelegramUser;
import com.codogrammer.irangoldtracker.service.AlertService;
import com.codogrammer.irangoldtracker.service.MarketPriceCache;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Walks the user through one alert at a time : item name, then the low price, then the high price.
 * Every method returns {@code true} when the conversation is over and the main menu can be shown again.
 */
@Service
@Slf4j
public class AddAlertHandler {

    public static final String PICK_ITEM_PREFIX = "ALERT_ITEM:";
    public static final String CANCEL = "ALERT_CANCEL";

    private static final int MAX_SUGGESTIONS = 6;
    private static final Set<String> CANCEL_WORDS = Set.of("لغو", "بیخیال", "cancel", "/cancel");

    private enum Step {
        NAME, ITEM, FROM, TO
    }

    private static final class Draft {

        private Step step = Step.NAME;
        private List<MarketItemMatch> candidates = List.of();
        private MarketItemMatch item;
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

        sender.send(
                chatId,
                """
                        ➕ افزودن هشدار (%d جای خالی داری)

                        اسم چیزی که میخوای رو بفرست، مثل :
                        طلای 18 عیار
                        دلار
                        BTC""".formatted(remaining),
                cancelKeyboard()
        );

        return false;
    }

    public boolean handleInput(Update update) {

        User from = update.getMessage().getFrom();
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

            case NAME -> onName(chatId, from, draft, text);

            case ITEM -> {
                sender.send(chatId, "از بین همون دکمه‌های بالا یکی رو انتخاب کن.", itemKeyboard(draft.candidates));
                yield false;
            }

            case FROM -> onFromPrice(chatId, draft, text);

            case TO -> onToPrice(chatId, from, draft, text);
        };
    }

    public boolean handlePick(Update update, int index) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Draft draft = drafts.get(chatId);

        if (draft == null || draft.step != Step.ITEM || index < 0 || index >= draft.candidates.size()) {
            sender.send(chatId, "این انتخاب قدیمیه، از اول شروع کن.");
            drafts.remove(chatId);
            return true;
        }

        return selectItem(chatId, from.getId(), draft, draft.candidates.get(index));
    }

    public boolean handleCancel(Update update) {
        return cancel(update.getCallbackQuery().getMessage().getChatId());
    }

    private boolean cancel(Long chatId) {

        drafts.remove(chatId);
        sender.send(chatId, "باشه، بیخیال هشدار شدم.");
        return true;
    }

    private boolean onName(Long chatId, User from, Draft draft, String text) {

        List<MarketItemMatch> candidates = Utils.findItems(marketPriceCache.getMarketPrices(), text, MAX_SUGGESTIONS);

        if (candidates.isEmpty()) {
            sender.send(
                    chatId,
                    "❌ «" + text + "» رو پیدا نکردم. اسمش رو از لیست قیمت‌ها بردار و دوباره بفرست.",
                    cancelKeyboard()
            );
            return false;
        }

        if (candidates.size() == 1) {
            return selectItem(chatId, from.getId(), draft, candidates.getFirst());
        }

        draft.candidates = candidates;
        draft.step = Step.ITEM;

        sender.send(chatId, "چندتا شبیه هم پیدا کردم، کدومش؟", itemKeyboard(candidates));
        return false;
    }

    private boolean selectItem(Long chatId, Long userId, Draft draft, MarketItemMatch match) {

        if (alertService.alreadyWatches(userId, match.market(), match.item().name())) {
            sender.send(chatId, "برای «" + match.item().name() + "» قبلاً هشدار داری. اول از مدیریت هشدار حذفش کن.");
            drafts.remove(chatId);
            return true;
        }

        draft.item = match;
        draft.candidates = List.of();
        draft.step = Step.FROM;

        sender.send(
                chatId,
                match.item().name() + " الان " + Utils.formatPrice(match.item().price()) + unit(match) + "\n\n"
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

        if (alertService.alreadyWatches(user.getId(), draft.item.market(), draft.item.item().name())) {
            sender.send(chatId, "برای «" + draft.item.item().name() + "» همین الان هشدار داری.");
            return true;
        }

        Alert alert = alertService.create(
                user,
                draft.item.market(),
                draft.item.item().name(),
                draft.item.item().symbol(),
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

    private Optional<BigDecimal> positivePrice(String text) {
        return Utils.parsePrice(text).filter(price -> price.signum() > 0);
    }

    private String unit(MarketItemMatch match) {
        return match.item().unit() == null ? "" : " " + match.item().unit();
    }

    private InlineKeyboardMarkup cancelKeyboard() {

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text("✖️ لغو")
                .callbackData(CANCEL)
                .build()));

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup itemKeyboard(List<MarketItemMatch> candidates) {

        List<InlineKeyboardRow> rows = IntStream.range(0, candidates.size())
                .mapToObj(index -> new InlineKeyboardRow(InlineKeyboardButton.builder()
                        .text(candidates.get(index).item().name()
                                + " (" + candidates.get(index).market().getPersianName() + ")")
                        .callbackData(PICK_ITEM_PREFIX + index)
                        .build()))
                .collect(Collectors.toCollection(ArrayList::new));

        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text("✖️ لغو")
                .callbackData(CANCEL)
                .build()));

        return new InlineKeyboardMarkup(rows);
    }
}
