package com.codogrammer.irangoldtracker.bot.alert;

import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class Draft {

    private static final int PAGE_SIZE = 8;

    private Step step = Step.MARKET;
    private MarketCurrencies market;
    private List<MarketItem> items = List.of();
    private int page;
    private MarketItem item;

    @Setter
    private BigDecimal fromPrice;

    public void selectMarket(
            MarketCurrencies market,
            List<MarketItem> items
    ) {
        this.market = market;
        this.items = items;
        this.page = 0;
        this.step = Step.ITEM;
    }

    public void selectItem(MarketItem item) {
        this.item = item;
        this.step = Step.FROM;
    }

    public void changePage(int page) {
        this.page = Math.max(
                0,
                Math.min(page, lastPage())
        );
    }

    public void resetToMarket() {
        this.market = null;
        this.items = List.of();
        this.page = 0;
        this.item = null;
        this.fromPrice = null;
        this.step = Step.MARKET;
    }

    public boolean canSelectItem(int index) {
        return step == Step.ITEM
                && index >= 0
                && index < items.size();
    }

    public int lastPage() {
        return Math.max(
                0,
                (items.size() - 1) / PAGE_SIZE
        );
    }
}