package com.vendex.overlap.service;

import com.vendex.events.contract.Action;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.overlap.config.OverlapProperties;
import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.InventorySnapshot;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.domain.OverlapCandidate;
import com.vendex.overlap.projection.OverlapProjectionStore;
import com.vendex.overlap.repository.OverlapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverlapReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    private OverlapProjectionStore projections;
    private OverlapRepository repository;
    private OverlapScorer scorer;
    private OutboxWriter outbox;
    private OverlapReconciler reconciler;

    @BeforeEach
    void setUp() {
        projections = mock(OverlapProjectionStore.class);
        repository = mock(OverlapRepository.class);
        scorer = mock(OverlapScorer.class);
        outbox = mock(OutboxWriter.class);
        reconciler = new OverlapReconciler(projections, repository, scorer,
                new OverlapProperties(), outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void materializesBestEligibleCandidateAndPublishesAdded() {
        Fixture fixture = fixture();
        InventorySnapshot lower = inventory(fixture, "80.00", InventoryPriority.NORMAL);
        InventorySnapshot best = inventory(fixture, "90.00", InventoryPriority.LIQUIDATE);
        DemandSnapshot demand = demand(fixture);
        arrangeActive(fixture);
        when(projections.inventoryForCard(fixture.event, fixture.seller, fixture.card))
                .thenReturn(List.of(lower, best));
        when(projections.demandForCard(fixture.buyer, fixture.card)).thenReturn(List.of(demand));
        when(scorer.score(lower, demand)).thenReturn(Optional.of(new BigDecimal("70.00")));
        when(scorer.score(best, demand)).thenReturn(Optional.of(new BigDecimal("90.00")));
        when(repository.findActivePair(fixture.event, fixture.seller, fixture.buyer))
                .thenReturn(List.of());
        when(repository.upsert(any(), eq(NOW))).thenAnswer(invocation ->
                overlap(invocation.getArgument(0)));

        reconciler.recompute(fixture.event, fixture.seller, fixture.buyer);

        ArgumentCaptor<OverlapCandidate> candidate = ArgumentCaptor.forClass(OverlapCandidate.class);
        verify(repository).upsert(candidate.capture(), eq(NOW));
        assertThat(candidate.getValue().inventory().itemId()).isEqualTo(best.itemId());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("overlap"), any(), eq(Topics.OVERLAP_FOUND), any(),
                event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(OverlapFound.class,
                found -> assertThat(found.action()).isEqualTo(Action.ADDED));
    }

    @Test
    void unchangedCandidateDoesNotWriteOrRepublish() {
        Fixture fixture = fixture();
        InventorySnapshot inventory = inventory(fixture, "90.00", InventoryPriority.NORMAL);
        DemandSnapshot demand = demand(fixture);
        arrangeActive(fixture);
        when(projections.inventoryForCard(fixture.event, fixture.seller, fixture.card))
                .thenReturn(List.of(inventory));
        when(projections.demandForCard(fixture.buyer, fixture.card)).thenReturn(List.of(demand));
        when(scorer.score(inventory, demand)).thenReturn(Optional.of(new BigDecimal("80.00")));
        Overlap existing = overlap(new OverlapCandidate(
                OverlapIds.forOpportunity(fixture.event, fixture.buyer, fixture.seller, fixture.card),
                fixture.event, fixture.buyer, fixture.seller, fixture.card,
                inventory, demand, new BigDecimal("80.00")));
        when(repository.findActivePair(fixture.event, fixture.seller, fixture.buyer))
                .thenReturn(List.of(existing));

        reconciler.recompute(fixture.event, fixture.seller, fixture.buyer);

        verify(repository, never()).upsert(any(), any());
        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void absentCandidateRetiresAndPublishesRemoved() {
        Fixture fixture = fixture();
        arrangeActive(fixture);
        when(projections.intersectCards(fixture.event, fixture.seller, fixture.buyer))
                .thenReturn(Set.of());
        Overlap existing = overlap(new OverlapCandidate(
                UUID.randomUUID(), fixture.event, fixture.buyer, fixture.seller, fixture.card,
                inventory(fixture, "90.00", InventoryPriority.NORMAL), demand(fixture),
                new BigDecimal("80.00")));
        when(repository.findActivePair(fixture.event, fixture.seller, fixture.buyer))
                .thenReturn(List.of(existing));
        when(repository.deactivate(existing.id(), NOW)).thenReturn(Optional.of(existing));

        reconciler.recompute(fixture.event, fixture.seller, fixture.buyer);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("overlap"), any(), eq(Topics.OVERLAP_FOUND), any(),
                event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(OverlapFound.class,
                found -> assertThat(found.action()).isEqualTo(Action.REMOVED));
    }

    private void arrangeActive(Fixture fixture) {
        when(projections.isVendorActive(fixture.event, fixture.seller)).thenReturn(true);
        when(projections.isVendorActive(fixture.event, fixture.buyer)).thenReturn(true);
        when(projections.intersectCards(fixture.event, fixture.seller, fixture.buyer))
                .thenReturn(Set.of(fixture.card));
    }

    private static Fixture fixture() {
        return new Fixture(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private static InventorySnapshot inventory(
            Fixture fixture, String price, InventoryPriority priority) {
        return new InventorySnapshot(UUID.randomUUID(), fixture.seller, null, fixture.card,
                CardCondition.NM, 2, new BigDecimal(price), priority, true, NOW);
    }

    private static DemandSnapshot demand(Fixture fixture) {
        return new DemandSnapshot(UUID.randomUUID(), fixture.buyer, fixture.card,
                CardCondition.LP, new BigDecimal("100.00"), 2, true, NOW);
    }

    private static Overlap overlap(OverlapCandidate candidate) {
        return new Overlap(candidate.id(), candidate.eventId(), candidate.buyerVendorId(),
                candidate.sellerVendorId(), candidate.cardId(), candidate.inventory().itemId(),
                candidate.demand().wantedCardId(), candidate.inventory().condition(),
                candidate.demand().minimumCondition(), candidate.inventory().quantity(),
                candidate.demand().quantityWanted(), candidate.inventory().askingPrice(),
                candidate.demand().maxBuyPrice(), candidate.inventory().priority(),
                candidate.score(), true, NOW, NOW);
    }

    private record Fixture(UUID event, UUID buyer, UUID seller, UUID card) {}
}
