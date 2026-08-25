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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AddAlertHandler {

    private static final String INSTRUCTIONS = """
            ➕ افزودن هشدار

            اسم چیزی که میخوای و بازه قیمتش رو با : برام بفرست
            نام : از : تا

            مثال
            طلای 18 عیار : 60000000 : 70000000

            هرکدوم از قیمت‌ها رد بشه خبرت میکنم.""";

    private final TelegramMessageSender sender;
    private final AlertService alertService;
    private final MarketPriceCache marketPriceCache;
    private final Set<Long> awaitingInput = ConcurrentHashMap.newKeySet();

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
        return awaitingInput.contains(chatId);
    }

    public void handle(Update update) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        alertService.register(from.getId(), chatId, from.getFirstName(), from.getUserName());

        if (alertService.hasReachedLimit(from.getId())) {
            sender.send(
                    chatId,
                    "سه هشدار فعال داری، بیشتر از این نمیشه. اول یکی رو از مدیریت هشدار حذف کن."
            );
            return;
        }

        awaitingInput.add(chatId);
        sender.send(chatId, INSTRUCTIONS);
    }

    public void handleInput(Update update) {

        User from = update.getMessage().getFrom();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        awaitingInput.remove(chatId);

        TelegramUser user = alertService.register(from.getId(), chatId, from.getFirstName(), from.getUserName());

        if (alertService.hasReachedLimit(user.getId())) {
            sender.send(chatId, "سه هشدار فعال داری، بیشتر از این نمیشه.");
            return;
        }

        String[] parts = text.split("[:،,]");

        if (parts.length != 3) {
            sender.send(chatId, "❌ قالب درست نیست. اینطوری بفرست : نام : از : تا");
            return;
        }

        Optional<BigDecimal> fromPrice = Utils.parsePrice(parts[1]);
        Optional<BigDecimal> toPrice = Utils.parsePrice(parts[2]);

        if (fromPrice.isEmpty() || toPrice.isEmpty()) {
            sender.send(chatId, "❌ قیمت‌ها رو نفهمیدم، فقط عدد بفرست.");
            return;
        }

        if (fromPrice.get().compareTo(toPrice.get()) >= 0) {
            sender.send(chatId, "❌ قیمت اول باید کمتر از قیمت دوم باشه.");
            return;
        }

        Optional<MarketItemMatch> match = Utils.findItem(marketPriceCache.getMarketPrices(), parts[0]);

        if (match.isEmpty()) {
            sender.send(chatId, "❌ «" + parts[0].trim() + "» رو پیدا نکردم. اسمش رو از لیست قیمت‌ها بردار.");
            return;
        }

        Alert alert = alertService.create(
                user,
                match.get().market(),
                match.get().item().name(),
                match.get().item().symbol(),
                fromPrice.get(),
                toPrice.get()
        );

        sender.send(
                chatId,
                "✅ هشدار ثبت شد\n"
                        + alert.getItemName()
                        + " : از " + Utils.formatPrice(alert.getFromPrice())
                        + " تا " + Utils.formatPrice(alert.getToPrice())
        );
    }
}
