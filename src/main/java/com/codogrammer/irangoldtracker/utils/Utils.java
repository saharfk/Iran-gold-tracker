package com.codogrammer.irangoldtracker.utils;

import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.dto.MarketItemMatch;
import com.codogrammer.irangoldtracker.dto.MarketResponse;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class Utils {

    private static final String PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹";
    private static final String ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩";

    public static String buildMessage(List<MarketItem> gold, MarketCurrencies marketCurrencies) {

        if (gold == null || gold.isEmpty()) {
            return "❌ اطلاعات طلا در دسترس نیست.";
        }

        return gold.stream()
                .map(Utils::formatItem)
                .collect(Collectors.joining("\n",
                        "🥇 قیمت " + marketCurrencies.getPersianName() + "\n\n تاریخ : " + JalaliDateTime.now() + "\n\n ",
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

        return parsePrice(price)
                .map(Utils::formatPrice)
                .orElse(price);
    }

    public static String formatPrice(BigDecimal price) {

        DecimalFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(price);
    }

    public static Optional<BigDecimal> parsePrice(String price) {

        if (price == null || price.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalizeDigits(price).replace(",", "").trim();

        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static String normalizeDigits(String text) {

        StringBuilder normalized = new StringBuilder(text.length());

        for (char character : text.toCharArray()) {

            int persianIndex = PERSIAN_DIGITS.indexOf(character);
            int arabicIndex = ARABIC_DIGITS.indexOf(character);

            if (persianIndex >= 0) {
                normalized.append((char) ('0' + persianIndex));
            } else if (arabicIndex >= 0) {
                normalized.append((char) ('0' + arabicIndex));
            } else {
                normalized.append(character);
            }
        }

        return normalized.toString();
    }

    public static List<MarketItem> itemsOf(MarketResponse prices, MarketCurrencies market) {

        if (prices == null) {
            return List.of();
        }

        List<MarketItem> items = switch (market) {
            case GOLD -> prices.gold();
            case CURRENCY -> prices.currency();
            case CRYPTO_CURRENCY -> prices.cryptocurrency();
        };

        return items == null ? List.of() : items;
    }

    public static Optional<MarketItemMatch> findItem(MarketResponse prices, String query) {

        String needle = query.trim().toLowerCase(Locale.ROOT);

        return Arrays.stream(MarketCurrencies.values())
                .flatMap(market -> itemsOf(prices, market).stream()
                        .filter(item -> matches(item, needle))
                        .map(item -> new MarketItemMatch(market, item)))
                .findFirst();
    }

    public static Optional<MarketItem> findItem(MarketResponse prices, MarketCurrencies market, String symbol, String name) {

        List<MarketItem> items = itemsOf(prices, market);

        return items.stream()
                .filter(item -> symbol != null && symbol.equalsIgnoreCase(item.symbol()))
                .findFirst()
                .or(() -> items.stream()
                        .filter(item -> name != null && name.equalsIgnoreCase(item.name()))
                        .findFirst());
    }

    private static boolean matches(MarketItem item, String needle) {

        return equalsIgnoreCase(item.symbol(), needle)
                || equalsIgnoreCase(item.name_en(), needle)
                || contains(item.name(), needle);
    }

    private static boolean equalsIgnoreCase(String value, String needle) {
        return value != null && value.trim().equalsIgnoreCase(needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).contains(needle);
    }
}
