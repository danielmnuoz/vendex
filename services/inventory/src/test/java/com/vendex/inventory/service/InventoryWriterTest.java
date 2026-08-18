package com.vendex.inventory.service;

import com.vendex.events.contract.Action;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.domain.InventoryPriority;
import com.vendex.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private InventoryRepository repository;
    private OutboxWriter outbox;
    private InventoryWriter writer;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        outbox = mock(OutboxWriter.class);
        writer = new InventoryWriter(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addWritesVendorKeyedFact() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        InventoryItemInput input = input(UUID.randomUUID());
        InventoryItem item = item(vendorId, cardId, input);
        when(repository.insert(vendorId, cardId, input, NOW)).thenReturn(item);

        assertThat(writer.add(vendorId, cardId, input)).isEqualTo(item);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("inventory_item"), eq(item.id().toString()),
                eq(Topics.INVENTORY_UPDATED), eq(vendorId.toString()), payload.capture());
        assertThat(payload.getValue()).isEqualTo(
                fact(item, input.eventId(), Action.ADDED));
    }

    @Test
    void movingItemBetweenEventsRemovesOldScopeAndAddsNewScope() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        InventoryItem current = item(vendorId, cardId, input(UUID.randomUUID()));
        InventoryItemInput movedInput = input(UUID.randomUUID());
        InventoryItem moved = item(current.id(), vendorId, cardId, movedInput);
        when(repository.update(current.id(), movedInput, NOW)).thenReturn(Optional.of(moved));

        writer.update(current, movedInput);

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(outbox, org.mockito.Mockito.times(2)).write(eq("inventory_item"),
                eq(current.id().toString()), eq(Topics.INVENTORY_UPDATED),
                eq(vendorId.toString()), payloads.capture());
        assertThat(payloads.getAllValues()).containsExactly(
                fact(current, current.eventId(), Action.REMOVED),
                fact(moved, moved.eventId(), Action.ADDED));
    }

    @Test
    void batchUsesOneTimestampAndReturnsAllRows() {
        UUID vendorId = UUID.randomUUID();
        UUID firstCard = UUID.randomUUID();
        UUID secondCard = UUID.randomUUID();
        InventoryItemInput firstInput = input(null);
        InventoryItemInput secondInput = input(UUID.randomUUID());
        InventoryItem first = item(vendorId, firstCard, firstInput);
        InventoryItem second = item(vendorId, secondCard, secondInput);
        when(repository.insert(vendorId, firstCard, firstInput, NOW)).thenReturn(first);
        when(repository.insert(vendorId, secondCard, secondInput, NOW)).thenReturn(second);

        List<InventoryItem> result = writer.addAll(vendorId, List.of(
                new InventoryWriter.BatchEntry(firstCard, firstInput),
                new InventoryWriter.BatchEntry(secondCard, secondInput)));

        assertThat(result).containsExactly(first, second);
        verify(outbox, org.mockito.Mockito.times(2)).write(
                eq("inventory_item"), org.mockito.ArgumentMatchers.anyString(),
                eq(Topics.INVENTORY_UPDATED), eq(vendorId.toString()),
                org.mockito.ArgumentMatchers.any(InventoryUpdated.class));
    }

    private static InventoryItemInput input(UUID eventId) {
        return new InventoryItemInput(eventId, CardCondition.NM, null, null,
                2, new BigDecimal("14.50"), InventoryPriority.NORMAL);
    }

    private static InventoryItem item(UUID vendorId, UUID cardId, InventoryItemInput input) {
        return item(UUID.randomUUID(), vendorId, cardId, input);
    }

    private static InventoryItem item(UUID id, UUID vendorId, UUID cardId, InventoryItemInput input) {
        return new InventoryItem(id, vendorId, cardId, input.eventId(), input.condition(),
                input.gradingCompany(), input.grade(), input.quantity(), input.askingPrice(),
                input.priority(), NOW, NOW);
    }

    private static InventoryUpdated fact(InventoryItem item, UUID eventId, Action action) {
        return new InventoryUpdated(item.id(), item.vendorId(), eventId, item.cardId(),
                item.condition().name(), item.quantity(), item.askingPrice(),
                item.priority().name().toLowerCase(), action, NOW);
    }
}
