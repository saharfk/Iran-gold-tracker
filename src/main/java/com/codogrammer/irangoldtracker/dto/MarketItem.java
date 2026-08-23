package com.codogrammer.irangoldtracker.dto;

public record MarketItem(
        String symbol,
        String name,
        String price,
        String changeValue,
        String changePercent,
        String unit,
        String date,
        String time
) {
}