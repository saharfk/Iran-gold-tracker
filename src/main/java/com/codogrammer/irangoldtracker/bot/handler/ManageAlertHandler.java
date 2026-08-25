package com.codogrammer.irangoldtracker.bot.handler;

import com.codogrammer.irangoldtracker.bot.TelegramMessageSender;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public class ManageAlertHandler {
    private final TelegramMessageSender sender;

    public ManageAlertHandler(TelegramMessageSender sender) {
        this.sender = sender;
    }

    public void handle(Update update) {

        Long chatId = update
                .getCallbackQuery()
                .getMessage()
                .getChatId();

        sender.send(
                chatId,
                """
                        🥇 قیمت طلا
                                                
                        فعلاً قیمت واقعی نداریم.
                        به زودی API اضافه می‌کنیم.
                        """
        );
    }
}
