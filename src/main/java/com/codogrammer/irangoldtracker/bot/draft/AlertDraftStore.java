package com.codogrammer.irangoldtracker.bot.draft;

import java.util.Optional;

public interface AlertDraftStore {

    Optional<AlertDraft> find(DraftKey key);

    void save(DraftKey key, AlertDraft draft);

    void remove(DraftKey key);
}
