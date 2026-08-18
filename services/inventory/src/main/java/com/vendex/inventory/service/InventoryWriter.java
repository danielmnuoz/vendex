package com.vendex.inventory.service;

import com.vendex.events.contract.Action;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Keeps JDBC mutations and their outbox facts inside the same transaction. */
@Component
public class InventoryWriter {

    private final InventoryRepository repository;
    private final OutboxWriter outbox;
    private final Clock clock;

    public InventoryWriter(InventoryRepository repository, OutboxWriter outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public InventoryItem add(UUID vendorId, UUID cardId, InventoryItemInput input) {
        Instant now = clock.instant();
        InventoryItem item = repository.insert(vendorId, cardId, input, now);
        publish(item, Action.ADDED, item.eventId(), now);
        return item;
    }

    @Transactional
    public InventoryItem update(InventoryItem current, InventoryItemInput input) {
        Instant now = clock.instant();
        InventoryItem updated = repository.update(current.id(), input, now)
                .orElseThrow(InventoryExceptions.ItemNotFoundException::new);
        if (Objects.equals(current.eventId(), updated.eventId())) {
            publish(updated, Action.UPDATED, updated.eventId(), now);
        } else {
            publish(current, Action.REMOVED, current.eventId(), now);
            publish(updated, Action.ADDED, updated.eventId(), now);
        }
        return updated;
    }

    @Transactional
    public InventoryItem remove(InventoryItem current) {
        Instant now = clock.instant();
        InventoryItem removed = repository.delete(current.id())
                .orElseThrow(InventoryExceptions.ItemNotFoundException::new);
        publish(removed, Action.REMOVED, current.eventId(), now);
        return removed;
    }

    @Transactional
    public List<InventoryItem> addAll(UUID vendorId, List<BatchEntry> entries) {
        Instant now = clock.instant();
        List<InventoryItem> imported = new ArrayList<>(entries.size());
        for (BatchEntry entry : entries) {
            InventoryItem item = repository.insert(vendorId, entry.cardId(), entry.input(), now);
            publish(item, Action.ADDED, item.eventId(), now);
            imported.add(item);
        }
        return List.copyOf(imported);
    }

    private void publish(InventoryItem item, Action action, UUID eventId, Instant timestamp) {
        outbox.write("inventory_item", item.id().toString(), Topics.INVENTORY_UPDATED,
                item.vendorId().toString(),
                new InventoryUpdated(item.id(), item.vendorId(), eventId, item.cardId(),
                        item.condition().name(), item.quantity(), item.askingPrice(),
                        item.priority().name().toLowerCase(), action, timestamp));
    }

    public record BatchEntry(UUID cardId, InventoryItemInput input) {}
}
