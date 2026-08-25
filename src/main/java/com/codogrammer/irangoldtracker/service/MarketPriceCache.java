package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.dto.MarketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class MarketPriceCache {

    private static final long REFRESH_RATE_MS = 60_000;

    private final MarketPriceService marketPriceService;
    private final AtomicReference<MarketResponse> snapshot = new AtomicReference<>();

    public MarketPriceCache(MarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    @Scheduled(initialDelay = 0, fixedRate = REFRESH_RATE_MS)
    public void refresh() {
        try {
            snapshot.set(marketPriceService.getMarketPrices());
            log.debug("Market prices cache refreshed");
        } catch (Exception e) {
            log.error("Failed to refresh market prices cache, keeping previous snapshot", e);
        }
    }

    public MarketResponse getMarketPrices() {

        MarketResponse cached = snapshot.get();

        if (cached != null) {
            return cached;
        }

        MarketResponse fresh = marketPriceService.getMarketPrices();
        snapshot.set(fresh);
        return fresh;
    }
}
