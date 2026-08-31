package com.codogrammer.irangoldtracker.bot.draft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drafts live only until the user finishes, gives up, or the bot restarts. Abandoned ones are
 * evicted so the map cannot grow without bound.
 */
@Component
@Slf4j
public class InMemoryAlertDraftStore implements AlertDraftStore {

    static final Duration TTL = Duration.ofMinutes(30);

    private static final long CLEANUP_RATE_MS = 300_000;

    private record Entry(AlertDraft draft, Instant touchedAt) {
    }

    private final Clock clock;
    private final Map<DraftKey, Entry> drafts = new ConcurrentHashMap<>();

    public InMemoryAlertDraftStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<AlertDraft> find(DraftKey key) {

        Entry entry = drafts.get(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (expired(entry)) {
            drafts.remove(key, entry);
            return Optional.empty();
        }

        drafts.put(key, new Entry(entry.draft(), clock.instant()));
        return Optional.of(entry.draft());
    }

    @Override
    public void save(DraftKey key, AlertDraft draft) {
        drafts.put(key, new Entry(draft, clock.instant()));
    }

    @Override
    public void remove(DraftKey key) {
        drafts.remove(key);
    }

    @Scheduled(initialDelay = CLEANUP_RATE_MS, fixedRate = CLEANUP_RATE_MS)
    public void evictExpired() {

        int before = drafts.size();
        drafts.values().removeIf(this::expired);

        if (before != drafts.size()) {
            log.debug("Evicted {} abandoned alert drafts", before - drafts.size());
        }
    }

    private boolean expired(Entry entry) {
        return entry.touchedAt().plus(TTL).isBefore(clock.instant());
    }
}
