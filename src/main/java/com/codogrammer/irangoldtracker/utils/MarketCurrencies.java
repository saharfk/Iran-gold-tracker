package com.codogrammer.irangoldtracker.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketCurrencies {

    GOLD("طلا", "🥇"),
    CURRENCY("ارز", "💵"),
    CRYPTO_CURRENCY("ارز دیجیتال", "🪙");

    private final String persianName;
    private final String emoji;
}
