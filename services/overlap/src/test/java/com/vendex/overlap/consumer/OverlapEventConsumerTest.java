package com.vendex.overlap.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import com.vendex.overlap.projection.ApplyResult;
import com.vendex.overlap.projection.OverlapProjectionStore;
import com.vendex.overlap.service.OverlapReconciler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverlapEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OverlapProjectionStore projections;
    private OverlapReconciler reconciler;
    private OverlapEventConsumer consumer;

    @BeforeEach
    void setUp() {
        projections = mock(OverlapProjectionStore.class);
        reconciler = mock(OverlapReconciler.class);
        consumer = new OverlapEventConsumer(objectMapper, projections, reconciler);
    }

    @Test
    void globalInventoryRecomputesOnlySellerDirectionsAtActiveEvents() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        InventoryUpdated fact = inventory(seller, null);
        when(projections.applyInventory(fact)).thenReturn(ApplyResult.APPLIED);
        when(projections.eventsForVendor(seller)).thenReturn(Set.of(event));
        when(projections.isVendorActive(event, seller)).thenReturn(true);
        when(projections.vendorsAtEvent(event)).thenReturn(Set.of(seller, buyer));

        consume(Topics.INVENTORY_UPDATED, fact);

        verify(reconciler).recompute(event, seller, buyer);
        verify(reconciler, never()).recompute(event, buyer, seller);
    }

    @Test
    void staleProjectionDoesNotRecompute() throws Exception {
        InventoryUpdated fact = inventory(UUID.randomUUID(), UUID.randomUUID());
        when(projections.applyInventory(fact)).thenReturn(ApplyResult.STALE);

        consume(Topics.INVENTORY_UPDATED, fact);

        verify(reconciler, never()).recompute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationRecomputesBothDirections() throws Exception {
        UUID event = UUID.randomUUID();
        UUID joined = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        EventVendorRegistered fact = new EventVendorRegistered(event, joined, Instant.EPOCH);
        when(projections.applyRoster(event, joined, true, Instant.EPOCH))
                .thenReturn(ApplyResult.DUPLICATE);
        when(projections.vendorsAtEvent(event)).thenReturn(Set.of(joined, other));

        consume(Topics.EVENT_VENDOR_REGISTERED, fact);

        verify(reconciler).recompute(event, joined, other);
        verify(reconciler).recompute(event, other, joined);
    }

    @Test
    void vendorUnregistrationRetiresButAttendeeUnregistrationIsIgnored() throws Exception {
        UUID event = UUID.randomUUID();
        UUID vendor = UUID.randomUUID();
        Instant at = Instant.EPOCH;
        EventParticipantUnregistered vendorFact = new EventParticipantUnregistered(
                event, vendor, ParticipantRole.VENDOR, at);
        when(projections.applyRoster(event, vendor, false, at))
                .thenReturn(ApplyResult.APPLIED);

        consume(Topics.EVENT_PARTICIPANT_UNREGISTERED, vendorFact);
        consume(Topics.EVENT_PARTICIPANT_UNREGISTERED,
                new EventParticipantUnregistered(
                        event, UUID.randomUUID(), ParticipantRole.ATTENDEE, at));

        verify(reconciler).retireVendor(event, vendor);
    }

    private void consume(String topic, Object value) throws Exception {
        consumer.onFact(new ConsumerRecord<>(topic, 0, 0L, "key",
                objectMapper.writeValueAsString(value)));
    }

    private static InventoryUpdated inventory(UUID vendorId, UUID eventId) {
        return new InventoryUpdated(UUID.randomUUID(), vendorId, eventId, UUID.randomUUID(),
                "NM", 1, new BigDecimal("10.00"), "normal", Action.UPDATED,
                Instant.EPOCH);
    }
}
