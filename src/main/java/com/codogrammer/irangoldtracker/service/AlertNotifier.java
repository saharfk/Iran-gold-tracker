package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AlertNotifier {

    private final AlertRepository alertRepository;
    private final TelegramMessageSender sender;

    public AlertNotifier(AlertRepository alertRepository, TelegramMessageSender sender) {
        this.alertRepository = alertRepository;
        this.sender = sender;
    }

    /**
     * Deleting the alert and notifying its owner share one transaction: the delete claims the alert so
     * a concurrent run cannot notify twice, and a failed send rolls the delete back so the next run
     * retries instead of losing the alert.
     */
    @Transactional
    public void notifyAndDelete(Alert alert, String text) {

        Long chatId = alert.getUser().getChatId();

        if (alertRepository.claim(alert.getId()) == 0) {
            log.debug("Alert {} was already handled elsewhere", alert.getId());
            return;
        }

        sender.send(chatId, text);
    }
}
