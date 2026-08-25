package com.codogrammer.irangoldtracker.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketCurrencies {
    GOLD("طلا"), CURRENCY("ارز"),CRYPTO_CURRENCY("ارز دیجیال");

    private final String persianName;
}