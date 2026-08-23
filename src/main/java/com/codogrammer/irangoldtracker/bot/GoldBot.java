package com.codogrammer.irangoldtracker.bot;

import com.codogrammer.irangoldtracker.bot.handler.AddAlertHandler;
import com.codogrammer.irangoldtracker.bot.handler.DollarPriceHandler;
import com.codogrammer.irangoldtracker.bot.handler.GoldPriceHandler;
import com.codogrammer.irangoldtracker.bot.handler.ManageAlertHandler;
import com.codogrammer.irangoldtracker.bot.menu.MainMenu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@Slf4j
public class GoldBot implements SpringLongPollingBot {

    private final String botToken;
    private final TelegramClient telegramClient;

    private final MainMenu mainMenu;
    private final GoldPriceHandler goldPriceHandler;
    private final DollarPriceHandler dollarPriceHandler;
    private final AddAlertHandler addAlertHandler;
    private final ManageAlertHandler manageAlertHandler;

    public GoldBot(
            @Value("${telegram.bot.token}") String botToken,
            MainMenu mainMenu,
            GoldPriceHandler goldPriceHandler,
            DollarPriceHandler dollarPriceHandler,
            AddAlertHandler addAlertHandler,
            ManageAlertHandler manageAlertHandler
    ) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);

        this.mainMenu = mainMenu;
        this.goldPriceHandler = goldPriceHandler;
        this.dollarPriceHandler = dollarPriceHandler;
        this.addAlertHandler = addAlertHandler;
        this.manageAlertHandler = manageAlertHandler;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updates -> updates.forEach(this::handleUpdate);
    }

    private void handleUpdate(Update update) {

        if (update.hasCallbackQuery()) {
            handleCallback(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update);
        }
    }

    private void handleCallback(Update update) {

        var callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        switch (callbackQuery.getData()) {

            case "GOLD_PRICE" -> goldPriceHandler.handle(update);

            case "DOLLAR_PRICE" -> dollarPriceHandler.handle(update);

            case "ADD_ALERT" -> addAlertHandler.handle(update);

            case "MANAGE_ALERT" -> manageAlertHandler.handle(update);
        }
        continueMainMenu(chatId);
    }

    private void handleMessage(Update update) {

        var message = update.getMessage();

        if (message.getText().equals("/start") || message.getText().equals("/menu")) {
            sendMainMenu(message.getChatId());
        }
    }

    private void sendMainMenu(Long chatId) {

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("🥇 Iran Gold Tracker\n" +
                      "\n" +
                      "سلام \n" +
                      "\n" +
                      "من می\u200Cتونم قیمت طلا و دلار رو برات بررسی کنم")
                .replyMarkup(mainMenu.getKeyboard())
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void continueMainMenu(Long chatId) {

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("🥇 Iran Gold Tracker\n" +
                      "\n" +
                      "دیگه چی میخوای جیگر \n")
                .replyMarkup(mainMenu.getKeyboard())
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}