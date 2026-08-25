package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.entity.AlertStatus;
import com.codogrammer.irangoldtracker.entity.TelegramUser;
import com.codogrammer.irangoldtracker.repository.AlertRepository;
import com.codogrammer.irangoldtracker.repository.TelegramUserRepository;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    public static final int MAX_ACTIVE_ALERTS = 3;

    private final TelegramUserRepository userRepository;
    private final AlertRepository alertRepository;

    public AlertService(TelegramUserRepository userRepository, AlertRepository alertRepository) {
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional
    public TelegramUser register(Long userId, Long chatId, String firstName, String username) {

        TelegramUser user = userRepository.findById(userId)
                .orElseGet(() -> new TelegramUser(userId, chatId, firstName, username));

        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setUsername(username);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Alert> activeAlerts(Long userId) {
        return alertRepository.findByUserIdAndStatusOrderByIdAsc(userId, AlertStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Alert> allActiveAlerts() {
        return alertRepository.findByStatusOrderByIdAsc(AlertStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean hasReachedLimit(Long userId) {
        return alertRepository.countByUserIdAndStatus(userId, AlertStatus.ACTIVE) >= MAX_ACTIVE_ALERTS;
    }

    @Transactional
    public Alert create(
            TelegramUser user,
            MarketCurrencies market,
            String itemName,
            String symbol,
            BigDecimal fromPrice,
            BigDecimal toPrice
    ) {
        return alertRepository.save(new Alert(user, market, itemName, symbol, fromPrice, toPrice));
    }

    @Transactional
    public Optional<Alert> delete(Long alertId, Long userId) {

        Optional<Alert> alert = alertRepository.findByIdAndUserId(alertId, userId);
        alert.ifPresent(alertRepository::delete);
        return alert;
    }

    @Transactional
    public void markDone(Alert alert, BigDecimal triggeredPrice) {

        alert.setStatus(AlertStatus.DONE);
        alert.setTriggeredAt(Instant.now());
        alert.setTriggeredPrice(triggeredPrice);
        alertRepository.save(alert);
    }
}
