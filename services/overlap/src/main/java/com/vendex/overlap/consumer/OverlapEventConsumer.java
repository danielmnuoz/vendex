package com.vendex.overlap.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import com.vendex.overlap.projection.ApplyResult;
import com.vendex.overlap.projection.OverlapProjectionStore;
import com.vendex.overlap.service.OverlapReconciler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** Applies one fact, then recomputes only the directed vendor pairs it affects. */
@Component
public class OverlapEventConsumer {

    private final ObjectMapper objectMapper;
    private final OverlapProjectionStore projections;
    private final OverlapReconciler reconciler;

    public OverlapEventConsumer(
            ObjectMapper objectMapper,
            OverlapProjectionStore projections,
            OverlapReconciler reconciler) {
        this.objectMapper = objectMapper;
        this.projections = projections;
        this.reconciler = reconciler;
    }

    @KafkaListener(topics = {
            Topics.INVENTORY_UPDATED,
            Topics.BUYLIST_UPDATED,
            Topics.EVENT_VENDOR_REGISTERED,
            Topics.EVENT_PARTICIPANT_UNREGISTERED
    })
    public void onFact(ConsumerRecord<String, String> record) {
        try {
            switch (record.topic()) {
                case Topics.INVENTORY_UPDATED -> inventory(
                        objectMapper.readValue(record.value(), InventoryUpdated.class));
                case Topics.BUYLIST_UPDATED -> demand(
                        objectMapper.readValue(record.value(), BuyListUpdated.class));
                case Topics.EVENT_VENDOR_REGISTERED -> registered(
                        objectMapper.readValue(record.value(), EventVendorRegistered.class));
                case Topics.EVENT_PARTICIPANT_UNREGISTERED -> unregistered(
                        objectMapper.readValue(record.value(),
                                EventParticipantUnregistered.class));
                default -> throw new IllegalArgumentException(
                        "unsupported overlap topic " + record.topic());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "invalid overlap source event on topic " + record.topic(), e);
        }
    }

    private void inventory(InventoryUpdated fact) {
        ApplyResult result = projections.applyInventory(fact);
        if (!result.shouldRecompute()) {
            return;
        }
        Set<UUID> events = fact.eventId() == null
                ? projections.eventsForVendor(fact.vendorId())
                : Set.of(fact.eventId());
        for (UUID eventId : events) {
            if (!projections.isVendorActive(eventId, fact.vendorId())) {
                continue;
            }
            for (UUID buyerId : projections.vendorsAtEvent(eventId)) {
                if (!buyerId.equals(fact.vendorId())) {
                    reconciler.recompute(eventId, fact.vendorId(), buyerId);
                }
            }
        }
    }

    private void demand(BuyListUpdated fact) {
        ApplyResult result = projections.applyDemand(fact);
        if (!result.shouldRecompute()) {
            return;
        }
        for (UUID eventId : projections.eventsForVendor(fact.vendorId())) {
            for (UUID sellerId : projections.vendorsAtEvent(eventId)) {
                if (!sellerId.equals(fact.vendorId())) {
                    reconciler.recompute(eventId, sellerId, fact.vendorId());
                }
            }
        }
    }

    private void registered(EventVendorRegistered fact) {
        ApplyResult result = projections.applyRoster(
                fact.eventId(), fact.vendorId(), true, fact.timestamp());
        if (!result.shouldRecompute()) {
            return;
        }
        for (UUID otherId : projections.vendorsAtEvent(fact.eventId())) {
            if (!otherId.equals(fact.vendorId())) {
                reconciler.recompute(fact.eventId(), fact.vendorId(), otherId);
                reconciler.recompute(fact.eventId(), otherId, fact.vendorId());
            }
        }
    }

    private void unregistered(EventParticipantUnregistered fact) {
        if (fact.role() != ParticipantRole.VENDOR) {
            return;
        }
        ApplyResult result = projections.applyRoster(
                fact.eventId(), fact.userId(), false, fact.timestamp());
        if (result.shouldRecompute()) {
            reconciler.retireVendor(fact.eventId(), fact.userId());
        }
    }
}
