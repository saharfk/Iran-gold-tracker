package com.codogrammer.irangoldtracker.dto;

import com.codogrammer.irangoldtracker.utils.MarketCurrencies;

public record MarketItemMatch(
        MarketCurrencies market,
        MarketItem item
) {
}
