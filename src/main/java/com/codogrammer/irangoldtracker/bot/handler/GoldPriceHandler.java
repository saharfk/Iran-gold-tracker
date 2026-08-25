package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.dto.MarketResponse;
import com.codogrammer.irangoldtracker.service.MarketPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
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
            MarketResponse prices = marketPriceService.getMarketPrices();
            sender.send(chatId, buildMessage(prices));

        } catch (Exception e) {
            log.error("Failed to fetch gold prices for chat {}", chatId, e);
            sender.send(
                    chatId,
                    "❌ دریافت اطلاعات با خطا مواجه شد."
            );
        }
    }

    private String buildMessage(MarketResponse prices) {

        List<MarketItem> gold = prices == null ? null : prices.gold();

        if (gold == null || gold.isEmpty()) {
            return "❌ اطلاعات طلا در دسترس نیست.";
        }

        return gold.stream()
                .map(this::formatItem)
                .collect(Collectors.joining("\n", "🥇 قیمت طلا\n\n", ""));
    }

    private String formatItem(MarketItem item) {

        String unit = item.unit() == null ? "" : " " + item.unit();
        return item.name() + " : " + formatPrice(item.price()) + unit;
    }

    private String formatPrice(String price) {

        if (price == null || price.isBlank()) {
            return "-";
        }

        String normalized = price.replace(",", "").trim();

        try {
            DecimalFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
            return format.format(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            return price;
        }
    }
}
