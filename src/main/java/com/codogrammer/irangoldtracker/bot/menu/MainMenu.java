package com.codogrammer.irangoldtracker.bot.menu;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class MainMenu {

    public InlineKeyboardMarkup getKeyboard() {

        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button("💵 قیمت دلار", "DOLLAR_PRICE"));
        row1.add(button("🥇 قیمت طلا", "GOLD_PRICE"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button("➕ افزودن آلرت", "ADD_ALERT"));
        row2.add(button("🔔 مدیریت آلرت", "MANAGE_ALERT"));

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row1);
        rows.add(row2);

        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
