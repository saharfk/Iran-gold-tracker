package com.codogrammer.irangoldtracker.bot.alert;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertDraftStore {

    private final Map<Long, Draft> drafts = new ConcurrentHashMap<>();

    public void start(Long chatId) {
        drafts.put(chatId, new Draft());
    }

    public Optional<Draft> get(Long chatId) {
        return Optional.ofNullable(drafts.get(chatId));
    }

    public boolean exists(Long chatId) {
        return drafts.containsKey(chatId);
    }

    public void remove(Long chatId) {
        drafts.remove(chatId);
    }
}