package com.codogrammer.irangoldtracker.bot.alert;

import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Setter
@Getter
public class Draft {
    private Step step = Step.MARKET;
    private MarketCurrencies market;
    private List<MarketItem> items = List.of();
    private int page;
    private MarketItem item;
    private BigDecimal fromPrice;
}