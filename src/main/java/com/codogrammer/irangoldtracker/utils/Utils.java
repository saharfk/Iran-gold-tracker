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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Utils {

    private static final String PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹";
    private static final String ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩";
    private static final int EXACT_SCORE = 2;

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

    public static List<MarketItemMatch> findItems(MarketResponse prices, String query, int limit) {

        String needle = normalizeName(query);

        if (needle.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<MarketItemMatch>> byScore = Arrays.stream(MarketCurrencies.values())
                .flatMap(market -> itemsOf(prices, market).stream()
                        .map(item -> new MarketItemMatch(market, item)))
                .filter(match -> score(match.item(), needle) > 0)
                .collect(Collectors.groupingBy(match -> score(match.item(), needle)));

        List<MarketItemMatch> result = byScore.getOrDefault(EXACT_SCORE, byScore.getOrDefault(1, List.of()));

        return result.size() > limit ? result.subList(0, limit) : result;
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

    private static int score(MarketItem item, String needle) {

        if (isExact(item.name(), needle) || isExact(item.symbol(), needle) || isExact(item.name_en(), needle)) {
            return EXACT_SCORE;
        }

        if (contains(item.name(), needle) || contains(item.name_en(), needle)) {
            return 1;
        }

        return 0;
    }

    private static boolean isExact(String value, String needle) {
        return value != null && normalizeName(value).equals(needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && normalizeName(value).contains(needle);
    }

    private static String normalizeName(String value) {

        return normalizeDigits(value)
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replace('\u200c', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
