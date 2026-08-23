package com.codogrammer.irangoldtracker.dto;

import java.util.List;

public record MarketResponse(
        List<MarketItem> data
) {
}