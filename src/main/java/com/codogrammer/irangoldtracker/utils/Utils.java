package com.codogrammer.irangoldtracker.utils;

import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.dto.MarketResponse;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Utils {
    public static String buildMessage(List<MarketItem> gold, MarketCurrencies marketCurrencies) {

        if (gold == null || gold.isEmpty()) {
            return "❌ اطلاعات طلا در دسترس نیست.";
        }

        return gold.stream()
                .map(Utils::formatItem)
                .collect(Collectors.joining("\n",
                        "🥇 قیمت " + marketCurrencies.getPersianName() +  "\n\n تاریخ : "+ "jalali date\n\n ",
                        ""));
    }

    public static String formatItem(MarketItem item) {

        String unit = item.unit() == null ? "" : " " + item.unit();
        return item.name() + " : " + formatPrice(item.price()) + unit;
    }

    public static String formatPrice(String price) {

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
