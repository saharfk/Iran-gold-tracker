package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.dto.MarketResponse;
import com.codogrammer.irangoldtracker.service.MarketPriceCache;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import static com.codogrammer.irangoldtracker.utils.Utils.buildMessage;

@Service
@Slf4j
public class AllPricesHandler {

    private final MarketPriceCache marketPriceCache;
    private final TelegramMessageSender sender;

    public AllPricesHandler(
            MarketPriceCache marketPriceCache,
            TelegramMessageSender sender
    ) {
        this.marketPriceCache = marketPriceCache;
        this.sender = sender;
    }

    public void handle(Update update, MarketCurrencies marketCurrencies) {

        Long chatId = update
                .getCallbackQuery()
                .getMessage()
                .getChatId();

        try {
            MarketResponse prices = marketPriceCache.getMarketPrices();
            switch (marketCurrencies) {
                case GOLD ->
                        sender.send(chatId, buildMessage(prices == null ? null : prices.gold(), MarketCurrencies.GOLD));
                case CURRENCY ->
                        sender.send(chatId, buildMessage(prices == null ? null : prices.currency(), MarketCurrencies.CURRENCY));
                case CRYPTO_CURRENCY ->
                        sender.send(chatId, buildMessage(prices == null ? null : prices.cryptocurrency(), MarketCurrencies.CRYPTO_CURRENCY));

            }
        } catch (Exception e) {
            log.error("Failed to fetch gold prices for chat {}", chatId, e);
            sender.send(
                    chatId,
                    "❌ دریافت اطلاعات با خطا مواجه شد."
            );
        }
    }
}