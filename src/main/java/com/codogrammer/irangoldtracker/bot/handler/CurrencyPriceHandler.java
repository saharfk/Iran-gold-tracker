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
public class CurrencyPriceHandler {
    private final TelegramMessageSender sender;
    private final MarketPriceCache marketPriceCache;

    public CurrencyPriceHandler(TelegramMessageSender sender, MarketPriceCache MarketPriceCache) {
        this.sender = sender;
        this.marketPriceCache = MarketPriceCache;
    }


    public void handle(Update update) {

        Long chatId = update
                .getCallbackQuery()
                .getMessage()
                .getChatId();

        try {
            MarketResponse prices = marketPriceCache.getMarketPrices();
            sender.send(chatId, buildMessage(prices == null ? null : prices.currency(), MarketCurrencies.CURRENCY));

        } catch (Exception e) {
            log.error("Failed to fetch currency prices for chat {}", chatId, e);
            sender.send(
                    chatId,
                    "❌ دریافت اطلاعات با خطا مواجه شد."
            );
        }
    }
}
