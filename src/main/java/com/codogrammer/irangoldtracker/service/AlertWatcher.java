package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.dto.MarketItem;
import com.codogrammer.irangoldtracker.dto.MarketResponse;
import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AlertWatcher {

    private static final long CHECK_RATE_MS = 60_000;

    private final AlertService alertService;
    private final MarketPriceCache marketPriceCache;
    private final TelegramMessageSender sender;

    public AlertWatcher(
            AlertService alertService,
            MarketPriceCache marketPriceCache,
            TelegramMessageSender sender
    ) {
        this.alertService = alertService;
        this.marketPriceCache = marketPriceCache;
        this.sender = sender;
    }

    @Scheduled(initialDelay = CHECK_RATE_MS, fixedRate = CHECK_RATE_MS)
    public void checkAlerts() {

        List<Alert> alerts = alertService.allActiveAlerts();

        if (alerts.isEmpty()) {
            return;
        }

        MarketResponse prices;

        try {
            prices = marketPriceCache.getMarketPrices();
        } catch (Exception e) {
            log.error("Cannot check alerts, market prices are unavailable", e);
            return;
        }

        alerts.forEach(alert -> check(alert, prices));
    }

    private void check(Alert alert, MarketResponse prices) {

        Optional<BigDecimal> price = Utils
                .findItem(prices, alert.getMarket(), alert.getSymbol(), alert.getItemName())
                .map(MarketItem::price)
                .flatMap(Utils::parsePrice);

        if (price.isEmpty()) {
            log.warn("No price found for alert {} ({})", alert.getId(), alert.getItemName());
            return;
        }

        BigDecimal current = price.get();

        if (current.compareTo(alert.getFromPrice()) > 0 && current.compareTo(alert.getToPrice()) < 0) {
            return;
        }

        try {
            sender.send(
                    alert.getUser().getChatId(),
                    "بدو بدو " + alert.getItemName() + " اومده رو " + Utils.formatPrice(current)
            );
            sender.send(
                    alert.getUser().getChatId(),
                    "این هشدار رو حذف کردم چون قیمتش رو دیدم ✅\n" + describe(alert)
            );
        } catch (Exception e) {
            log.error("Failed to notify user {} about alert {}", alert.getUser().getId(), alert.getId(), e);
            return;
        }

        alertService.markDone(alert, current);
    }

    private String describe(Alert alert) {
        return alert.getItemName()
                + " : از " + Utils.formatPrice(alert.getFromPrice())
                + " تا " + Utils.formatPrice(alert.getToPrice());
    }
}
