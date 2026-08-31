package com.codogrammer.irangoldtracker.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramConfiguration {

    @Bean
    public TelegramClient telegramClient(
            @Value("${telegram.bot.token}") String botToken
    ) {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public RestClient brsRestClient(
            @Value("${brs.api.base-url}") String baseUrl
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
