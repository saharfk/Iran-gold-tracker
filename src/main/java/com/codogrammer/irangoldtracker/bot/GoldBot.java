package com.codogrammer.irangoldtracker.bot;

import com.codogrammer.irangoldtracker.bot.handler.*;
import com.codogrammer.irangoldtracker.bot.menu.MainMenu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Consumer;

@Component
@Slf4j
public class GoldBot implements SpringLongPollingBot {

    private final String botToken;

    private final TelegramMessageSender sender;
    private final MainMenu mainMenu;
    private final GoldPriceHandler goldPriceHandler;
    private final CurrencyPriceHandler currencyPriceHandler;
    private final CryptoCurrencyPriceHandler cryptoCurrencyPriceHandler;
    private final AddAlertHandler addAlertHandler;
    private final ManageAlertHandler manageAlertHandler;

    public GoldBot(
            @Value("${telegram.bot.token}") String botToken,
            TelegramMessageSender sender,
            MainMenu mainMenu,
            GoldPriceHandler goldPriceHandler,
            CurrencyPriceHandler currencyPriceHandler,
            CryptoCurrencyPriceHandler cryptoCurrencyPriceHandler,
            AddAlertHandler addAlertHandler,
            ManageAlertHandler manageAlertHandler
    ) {
        this.botToken = botToken;
        this.sender = sender;
        this.mainMenu = mainMenu;
        this.goldPriceHandler = goldPriceHandler;
        this.currencyPriceHandler = currencyPriceHandler;
        this.cryptoCurrencyPriceHandler = cryptoCurrencyPriceHandler;
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
        String data = callbackQuery.getData();

        sender.acknowledge(callbackQuery.getId());

        if (interactionFinished(update, data)) {
            continueMainMenu(chatId);
        }
    }

    private boolean interactionFinished(Update update, String data) {

        if (data.startsWith(ManageAlertHandler.DELETE_ALERT_PREFIX)) {
            return handleDeleteAlert(update, data);
        }

        if (data.startsWith(AddAlertHandler.PICK_ITEM_PREFIX)) {
            return handlePickItem(update, data);
        }

        if (data.startsWith(AddAlertHandler.PICK_MARKET_PREFIX)) {
            return addAlertHandler.handlePickMarket(update, data.substring(AddAlertHandler.PICK_MARKET_PREFIX.length()));
        }

        if (data.startsWith(AddAlertHandler.PAGE_PREFIX)) {
            return handleAlertPage(update, data);
        }

        return switch (data) {

            case "GOLD_PRICE" -> showPrices(update, goldPriceHandler::handle);

            case "CURRENCY_PRICE" -> showPrices(update, currencyPriceHandler::handle);

            case "CRYPTO_CURRENCY_PRICE" -> showPrices(update, cryptoCurrencyPriceHandler::handle);

            case "ADD_ALERT" -> addAlertHandler.handle(update);

            case "MANAGE_ALERT" -> manageAlertHandler.handle(update);

            case AddAlertHandler.BACK_TO_MARKETS -> addAlertHandler.handleBackToMarkets(update);

            case AddAlertHandler.CANCEL -> addAlertHandler.handleCancel(update);

            case ManageAlertHandler.DONE -> true;

            default -> {
                log.warn("Unknown callback: {}", data);
                yield true;
            }
        };
    }

    private boolean showPrices(Update update, Consumer<Update> handler) {

        sender.send(update.getCallbackQuery().getMessage().getChatId(), "بذار ببینم قیمتا چطورین الان میگم بهت، صبر کن");
        handler.accept(update);
        return true;
    }

    private boolean handleDeleteAlert(Update update, String data) {

        String alertId = data.substring(ManageAlertHandler.DELETE_ALERT_PREFIX.length());

        try {
            return manageAlertHandler.handleDelete(update, Long.parseLong(alertId));
        } catch (NumberFormatException e) {
            log.warn("Unexpected delete alert callback: {}", data);
            return true;
        }
    }

    private boolean handlePickItem(Update update, String data) {

        String index = data.substring(AddAlertHandler.PICK_ITEM_PREFIX.length());

        try {
            return addAlertHandler.handlePick(update, Integer.parseInt(index));
        } catch (NumberFormatException e) {
            log.warn("Unexpected pick item callback: {}", data);
            return true;
        }
    }

    private boolean handleAlertPage(Update update, String data) {

        String page = data.substring(AddAlertHandler.PAGE_PREFIX.length());

        try {
            return addAlertHandler.handlePage(update, Integer.parseInt(page));
        } catch (NumberFormatException e) {
            log.warn("Unexpected alert page callback: {}", data);
            return true;
        }
    }

    private void handleMessage(Update update) {

        var message = update.getMessage();

        if (message.getText().equals("/start") || message.getText().equals("/menu")) {
            sendMainMenu(message.getChatId());
            return;
        }

        if (addAlertHandler.isAwaitingInput(message.getChatId(), message.getFrom().getId())
                && addAlertHandler.handleInput(update)) {
            continueMainMenu(message.getChatId());
        }
    }

    private void sendMainMenu(Long chatId) {

        sender.send(
                chatId,
                "🥇 Iran Gold Tracker\n" +
                        "\n" +
                        "سلام \n" +
                        "\n" +
                        "من می\u200Cتونم قیمت طلا و ارز رو برات بررسی کنم",
                mainMenu.getKeyboard()
        );
    }

    private void continueMainMenu(Long chatId) {

        sender.send(
                chatId,
                "🥇 Iran Gold Tracker\n" +
                        "\n" +
                        "دیگه چی میخوای جیگر \n",
                mainMenu.getKeyboard()
        );
    }
}