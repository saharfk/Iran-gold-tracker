package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.service.MarketPriceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public class GoldPriceHandler {

    private final MarketPriceService marketPriceService;
    private final TelegramMessageSender sender;

    public GoldPriceHandler(
            MarketPriceService marketPriceService,
            TelegramMessageSender sender
    ) {
        this.marketPriceService = marketPriceService;
        this.sender = sender;
    }

    public void handle(Update update) {

        Long chatId = update
                .getCallbackQuery()
                .getMessage()
                .getChatId();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String response = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(marketPriceService.getMarketPrices());

            response = response.substring(0, response.length() / 7);
            sender.send(chatId, response);

        } catch (Exception e) {
            sender.send(
                    chatId,
                    "❌ دریافت اطلاعات با خطا مواجه شد."
            );
        }
    }
}
