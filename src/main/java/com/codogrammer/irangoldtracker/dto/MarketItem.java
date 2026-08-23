package com.codogrammer.irangoldtracker.dto;

public record MarketItem(
         String date,
         String time,
         Integer time_unix,
         String symbol,
         String name_en,
         String name,
         String price,
         Double change_percent,
         Object market_cap,
         String unit,
         String description
) {
}