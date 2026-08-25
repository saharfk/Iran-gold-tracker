package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.service.AlertService;
import com.codogrammer.irangoldtracker.utils.Utils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ManageAlertHandler {

    public static final String DELETE_ALERT_PREFIX = "DELETE_ALERT:";
    public static final String DONE = "ALERTS_DONE";

    private final TelegramMessageSender sender;
    private final AlertService alertService;

    public ManageAlertHandler(TelegramMessageSender sender, AlertService alertService) {
        this.sender = sender;
        this.alertService = alertService;
    }

    public boolean handle(Update update) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        alertService.register(from.getId(), chatId, from.getFirstName(), from.getUserName());

        List<Alert> alerts = alertService.alerts(from.getId());

        if (alerts.isEmpty()) {
            sender.send(chatId, "🔔 هیچ هشداری نداری.");
            return true;
        }

        sender.send(chatId, "🔔 هشدارهات، هرکدوم رو بزنی حذف میشه", keyboard(alerts));
        return false;
    }

    public boolean handleDelete(Update update, Long alertId) {

        User from = update.getCallbackQuery().getFrom();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Optional<Alert> deleted = alertService.delete(alertId, from.getId());

        if (deleted.isEmpty()) {
            sender.send(chatId, "❌ این هشدار رو پیدا نکردم.");
            return true;
        }

        sender.send(chatId, "🗑 حذف شد\n" + describe(deleted.get()));

        List<Alert> remaining = alertService.alerts(from.getId());

        if (remaining.isEmpty()) {
            return true;
        }

        sender.send(chatId, "🔔 بقیه هشدارهات", keyboard(remaining));
        return false;
    }

    private InlineKeyboardMarkup keyboard(List<Alert> alerts) {

        List<InlineKeyboardRow> rows = alerts.stream()
                .map(alert -> new InlineKeyboardRow(InlineKeyboardButton.builder()
                        .text("🗑 " + describe(alert))
                        .callbackData(DELETE_ALERT_PREFIX + alert.getId())
                        .build()))
                .collect(Collectors.toCollection(ArrayList::new));

        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text("✅ تموم شد")
                .callbackData(DONE)
                .build()));

        return new InlineKeyboardMarkup(rows);
    }

    private String describe(Alert alert) {
        return alert.getItemName()
                + " : از " + Utils.formatPrice(alert.getFromPrice())
                + " تا " + Utils.formatPrice(alert.getToPrice());
    }
}
