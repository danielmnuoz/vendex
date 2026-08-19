package com.vendex.cardcatalog.sync;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.repository.CardRepository;
import com.vendex.cardcatalog.repository.CatalogSyncStateRepository;
import com.vendex.cardcatalog.tcgdex.TcgDexClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refreshes the local catalog outside the request path. Upstream data is
 * fetched completely before the transactional upsert starts, and a
 * database-backed lease makes the schedule safe across service replicas.
 */
@Service
@Profile("!seed")
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);
    private static final String SOURCE = "tcgdex";

    private final TcgDexClient tcgdex;
    private final CardRepository cards;
    private final CatalogSyncStateRepository syncState;
    private final CardCatalogProperties properties;
    private final Clock clock;

    public CatalogSyncService(
            TcgDexClient tcgdex,
            CardRepository cards,
            CatalogSyncStateRepository syncState,
            CardCatalogProperties properties,
            Clock clock) {
        this.tcgdex = tcgdex;
        this.cards = cards;
        this.syncState = syncState;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${card-catalog.sync.initial-delay:PT1M}",
            fixedDelayString = "${card-catalog.sync.interval:PT24H}")
    public void scheduledSync() {
        try {
            synchronizeOnce();
        } catch (RuntimeException failure) {
            log.error("catalog sync: scheduled refresh failed", failure);
        }
    }

    public SyncResult synchronizeOnce() {
        CardCatalogProperties.Sync sync = properties.sync();
        if (!sync.enabled()) {
            return new SyncResult(Status.DISABLED, 0);
        }
        requirePositive(sync.leaseDuration(), "card-catalog.sync.lease-duration");

        Instant startedAt = clock.instant();
        UUID owner = syncState.tryAcquire(SOURCE, startedAt, sync.leaseDuration()).orElse(null);
        if (owner == null) {
            log.info("catalog sync: another replica owns the active lease; skipping");
            return new SyncResult(Status.LEASE_HELD, 0);
        }

        try {
            log.info("catalog sync: fetching complete TCGdex snapshot");
            Map<String, String> series = properties.seed().enrichSeries()
                    ? tcgdex.fetchSeriesIndex()
                    : Map.of();
            List<CardSeed> snapshot = tcgdex.fetchAllSeeds(series, 0);
            if (snapshot.isEmpty()) {
                throw new IllegalStateException("TCGdex returned an empty card snapshot");
            }
            cards.upsertAll(snapshot);
            syncState.markSucceeded(SOURCE, owner, clock.instant(), snapshot.size());
            log.info("catalog sync: completed {} card upserts", snapshot.size());
            return new SyncResult(Status.COMPLETED, snapshot.size());
        } catch (RuntimeException failure) {
            recordFailure(owner, failure);
            throw failure;
        }
    }

    private void recordFailure(UUID owner, RuntimeException failure) {
        try {
            syncState.markFailed(SOURCE, owner, clock.instant(), failure.getMessage());
        } catch (RuntimeException stateFailure) {
            failure.addSuppressed(stateFailure);
            log.error("catalog sync: failed to persist failure state", stateFailure);
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " must be a positive duration");
        }
    }

    public enum Status {
        COMPLETED,
        LEASE_HELD,
        DISABLED
    }

    public record SyncResult(Status status, int cardCount) {}
}
