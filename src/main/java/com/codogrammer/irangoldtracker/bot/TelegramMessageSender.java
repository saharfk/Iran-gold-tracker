package com.codogrammer.irangoldtracker.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramMessageSender {

    private final TelegramClient telegramClient;

    public TelegramMessageSender(
            @Value("${telegram.bot.token}") String botToken
    ) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    public void send(Long chatId, String text) {

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
