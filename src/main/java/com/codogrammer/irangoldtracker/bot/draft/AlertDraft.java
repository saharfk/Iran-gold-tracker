package com.codogrammer.irangoldtracker.bot.draft;

import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the user has answered so far while adding one alert.
 */
@Getter
@Setter
public class AlertDraft {

    public enum Step {
        MARKET, ITEM, FROM, TO
    }

    private Step step = Step.MARKET;
    private MarketCurrencies market;
    private List<MarketItem> items = List.of();
    private int page;
    private MarketItem item;
    private BigDecimal fromPrice;

    public void backToMarkets() {
        step = Step.MARKET;
        market = null;
        items = List.of();
        page = 0;
        item = null;
        fromPrice = null;
    }
}
