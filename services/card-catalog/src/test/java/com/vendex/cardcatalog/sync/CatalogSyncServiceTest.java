package com.vendex.cardcatalog.sync;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.repository.CardRepository;
import com.vendex.cardcatalog.repository.CatalogSyncStateRepository;
import com.vendex.cardcatalog.tcgdex.TcgDexClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Duration LEASE = Duration.ofHours(2);

    @Mock
    TcgDexClient tcgdex;

    @Mock
    CardRepository cards;

    @Mock
    CatalogSyncStateRepository syncState;

    private CatalogSyncService service;

    @BeforeEach
    void setUp() {
        service = new CatalogSyncService(
                tcgdex,
                cards,
                syncState,
                properties(true),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fetchesCompleteSnapshotThenCommitsAndRecordsSuccess() {
        UUID owner = UUID.randomUUID();
        Map<String, String> series = Map.of("sv", "Scarlet & Violet");
        List<CardSeed> snapshot = List.of(seed("sv01-001"), seed("sv01-002"));
        when(syncState.tryAcquire("tcgdex", NOW, LEASE)).thenReturn(Optional.of(owner));
        when(tcgdex.fetchSeriesIndex()).thenReturn(series);
        when(tcgdex.fetchAllSeeds(series, 0)).thenReturn(snapshot);

        CatalogSyncService.SyncResult result = service.synchronizeOnce();

        assertThat(result.status()).isEqualTo(CatalogSyncService.Status.COMPLETED);
        assertThat(result.cardCount()).isEqualTo(2);
        verify(cards).upsertAll(snapshot);
        verify(syncState).markSucceeded("tcgdex", owner, NOW, 2);
    }

    @Test
    void upstreamFailureWritesNoCardsAndRecordsFailure() {
        UUID owner = UUID.randomUUID();
        RuntimeException upstream = new RuntimeException("TCGdex unavailable");
        when(syncState.tryAcquire("tcgdex", NOW, LEASE)).thenReturn(Optional.of(owner));
        when(tcgdex.fetchSeriesIndex()).thenThrow(upstream);

        assertThatThrownBy(service::synchronizeOnce).isSameAs(upstream);

        verify(cards, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
        verify(syncState).markFailed("tcgdex", owner, NOW, "TCGdex unavailable");
    }

    @Test
    void activeReplicaLeaseSkipsAllExternalWork() {
        when(syncState.tryAcquire("tcgdex", NOW, LEASE)).thenReturn(Optional.empty());

        assertThat(service.synchronizeOnce().status())
                .isEqualTo(CatalogSyncService.Status.LEASE_HELD);

        verify(tcgdex, never()).fetchSeriesIndex();
        verify(cards, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void disabledScheduleDoesNotAcquireLeaseOrContactUpstream() {
        service = new CatalogSyncService(
                tcgdex,
                cards,
                syncState,
                properties(false),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.synchronizeOnce().status())
                .isEqualTo(CatalogSyncService.Status.DISABLED);

        verify(syncState, never()).tryAcquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(tcgdex, never()).fetchSeriesIndex();
        verify(cards, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void emptySnapshotIsRecordedAsFailureInsteadOfFalseSuccess() {
        UUID owner = UUID.randomUUID();
        Map<String, String> series = Map.of();
        when(syncState.tryAcquire("tcgdex", NOW, LEASE)).thenReturn(Optional.of(owner));
        when(tcgdex.fetchSeriesIndex()).thenReturn(series);
        when(tcgdex.fetchAllSeeds(series, 0)).thenReturn(List.of());

        assertThatThrownBy(service::synchronizeOnce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TCGdex returned an empty card snapshot");

        verify(cards, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
        verify(syncState).markFailed(
                "tcgdex", owner, NOW, "TCGdex returned an empty card snapshot");
    }

    private static CardCatalogProperties properties(boolean enabled) {
        return new CardCatalogProperties(
                new CardCatalogProperties.Tcgdex(
                        "https://api.tcgdex.net/v2/en", Duration.ofSeconds(5), Duration.ofSeconds(20)),
                new CardCatalogProperties.Cache(true, Duration.ofHours(1)),
                new CardCatalogProperties.Search(25, 100),
                new CardCatalogProperties.Seed(0, false, true),
                new CardCatalogProperties.Sync(
                        enabled, Duration.ofMinutes(1), Duration.ofDays(1), LEASE));
    }

    private static CardSeed seed(String id) {
        return new CardSeed(
                id, "Pikachu", "sv01", "Scarlet & Violet", "Scarlet & Violet",
                null, null, null, null);
    }
}
