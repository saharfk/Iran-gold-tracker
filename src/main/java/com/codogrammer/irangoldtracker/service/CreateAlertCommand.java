package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.utils.MarketCurrencies;

import java.math.BigDecimal;

public record CreateAlertCommand(
        Long userId,
        Long chatId,
        String firstName,
        String username,
        MarketCurrencies market,
        String itemName,
        String symbol,
        BigDecimal fromPrice,
        BigDecimal toPrice
) {
}
