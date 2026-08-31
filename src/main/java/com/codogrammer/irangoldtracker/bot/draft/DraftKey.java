package com.codogrammer.irangoldtracker.bot.draft;

/**
 * A conversation belongs to one user inside one chat: in a group two users must not share a draft.
 */
public record DraftKey(Long chatId, Long userId) {
}
