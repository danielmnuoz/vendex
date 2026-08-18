package com.vendex.overlap.service;

import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.repository.OverlapRepository;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.Topics;
import com.vendex.overlap.domain.SavedOverlap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OverlapQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
    private OverlapRepository repository;
    private OutboxWriter outbox;
    private OverlapQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(OverlapRepository.class);
        outbox = mock(OutboxWriter.class);
        service = new OverlapQueryService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), outbox);
    }

    @Test
    void listUsesLookaheadPagination() {
        UUID vendor = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        when(repository.listForVendor(vendor, event, 3, 0))
                .thenReturn(List.of(overlap(vendor, true), overlap(vendor, true),
                        overlap(vendor, true)));

        var page = service.getForVendor(vendor, event, 2, 0);

        assertThat(page.overlaps()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPageOffset()).isEqualTo(2);
    }

    @Test
    void onlyParticipantCanSaveActiveOverlap() {
        UUID buyer = UUID.randomUUID();
        Overlap overlap = overlap(buyer, true);
        when(repository.findById(overlap.id())).thenReturn(Optional.of(overlap));

        assertThatThrownBy(() -> service.save(UUID.randomUUID(), overlap.id()))
                .isInstanceOf(OverlapExceptions.OwnershipException.class);
        verify(repository).findById(overlap.id());
    }

    @Test
    void inactiveOverlapCannotBeNewlySaved() {
        UUID buyer = UUID.randomUUID();
        Overlap overlap = overlap(buyer, false);
        when(repository.findById(overlap.id())).thenReturn(Optional.of(overlap));

        assertThatThrownBy(() -> service.save(buyer, overlap.id()))
                .isInstanceOf(OverlapExceptions.InactiveException.class);
    }

    @Test
    void firstSavePublishesEventPlanFactAndDuplicateDoesNot() {
        UUID buyer = UUID.randomUUID();
        Overlap overlap = overlap(buyer, true);
        SavedOverlap saved = new SavedOverlap(
                UUID.randomUUID(), buyer, overlap, NOW);
        when(repository.findById(overlap.id())).thenReturn(Optional.of(overlap));
        when(repository.save(buyer, overlap, NOW))
                .thenReturn(new OverlapRepository.SaveResult(saved, true))
                .thenReturn(new OverlapRepository.SaveResult(saved, false));

        service.save(buyer, overlap.id());
        service.save(buyer, overlap.id());

        verify(outbox, times(1)).write(
                org.mockito.ArgumentMatchers.eq("saved_overlap"),
                org.mockito.ArgumentMatchers.eq(saved.id().toString()),
                org.mockito.ArgumentMatchers.eq(Topics.OVERLAP_SAVED),
                org.mockito.ArgumentMatchers.eq(buyer.toString()),
                org.mockito.ArgumentMatchers.any(OverlapSaved.class));
    }

    private static Overlap overlap(UUID buyer, boolean active) {
        return new Overlap(UUID.randomUUID(), UUID.randomUUID(), buyer, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CardCondition.NM,
                CardCondition.LP, 2, 1, new BigDecimal("20.00"),
                new BigDecimal("25.00"), InventoryPriority.NORMAL,
                new BigDecimal("80.00"), active, NOW, NOW);
    }
}
