package com.codogrammer.irangoldtracker.dto;

import java.util.List;

public record MarketResponse(
        List<MarketItem> gold,
        List<MarketItem> currency,
        List<MarketItem> cryptocurrency
) {
}