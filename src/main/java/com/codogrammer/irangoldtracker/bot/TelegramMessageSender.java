package com.codogrammer.irangoldtracker.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@Slf4j
public class TelegramMessageSender {

    private final TelegramClient telegramClient;

    public TelegramMessageSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void send(Long chatId, String text) {
        send(chatId, text, null);
    }

    public void send(Long chatId, String text, InlineKeyboardMarkup keyboard) {

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops the loading spinner Telegram shows on a tapped inline button. Failing to acknowledge is
     * not worth aborting the update for, so it is only logged.
     */
    public void acknowledge(String callbackQueryId) {

        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .build();

        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Could not answer callback query {}", callbackQueryId, e);
        }
    }
}
