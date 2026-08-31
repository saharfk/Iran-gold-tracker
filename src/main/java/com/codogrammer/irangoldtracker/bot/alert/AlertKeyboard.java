package com.codogrammer.irangoldtracker.bot.alert;


import com.codogrammer.irangoldtracker.bot.handler.AddAlertHandler;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class AlertKeyboard {

    private static final int PAGE_SIZE = 8;

    public InlineKeyboardMarkup markets() {

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (MarketCurrencies market : MarketCurrencies.values()) {
            rows.add(
                    new InlineKeyboardRow(
                            button(
                                    market.getPersianName(),
                                    AddAlertHandler.PICK_MARKET_PREFIX
                                            + market.name()
                            )
                    )
            );
        }

        rows.add(
                new InlineKeyboardRow(
                        button("✖️ لغو", AddAlertHandler.CANCEL)
                )
        );

        return new InlineKeyboardMarkup(rows);
    }

    public InlineKeyboardMarkup items(Draft draft) {

        int from = draft.getPage() * PAGE_SIZE;
        int to = Math.min(
                from + PAGE_SIZE,
                draft.getItems().size()
        );

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int index = from; index < to; index++) {

            var item = draft.getItems().get(index);

            rows.add(
                    new InlineKeyboardRow(
                            button(
                                    item.name()
                                            + " ("
                                            + com.codogrammer.irangoldtracker.utils.Utils
                                            .formatPrice(item.price())
                                            + ")",
                                    AddAlertHandler.PICK_ITEM_PREFIX + index
                            )
                    )
            );
        }

        InlineKeyboardRow navigation = new InlineKeyboardRow();

        if (draft.getPage() > 0) {
            navigation.add(
                    button(
                            "⬅️ قبلی",
                            AddAlertHandler.PAGE_PREFIX
                                    + (draft.getPage() - 1)
                    )
            );
        }

        if (draft.getPage() < draft.lastPage()) {
            navigation.add(
                    button(
                            "بعدی ➡️",
                            AddAlertHandler.PAGE_PREFIX
                                    + (draft.getPage() + 1)
                    )
            );
        }

        if (!navigation.isEmpty()) {
            rows.add(navigation);
        }

        rows.add(
                new InlineKeyboardRow(
                        button(
                                "🔙 بازارها",
                                AddAlertHandler.BACK_TO_MARKETS
                        )
                )
        );

        rows.add(
                new InlineKeyboardRow(
                        button("✖️ لغو", AddAlertHandler.CANCEL)
                )
        );

        return new InlineKeyboardMarkup(rows);
    }

    public InlineKeyboardMarkup cancel() {

        return new InlineKeyboardMarkup(
                List.of(
                        new InlineKeyboardRow(
                                button("✖️ لغو", AddAlertHandler.CANCEL)
                        )
                )
        );
    }

    private InlineKeyboardButton button(
            String text,
            String callbackData
    ) {

        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}