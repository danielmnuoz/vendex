package com.vendex.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.EventCreated;
import com.vendex.events.contract.EventUpdated;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.Topics;
import com.vendex.notification.service.NotificationProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock NotificationProjectionService projections;
    private ObjectMapper mapper;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        consumer = new NotificationEventConsumer(mapper, projections);
    }

    @Test
    void dispatchesEveryProjectionTopic() throws Exception {
        EventCreated created = new EventCreated(UUID.randomUUID(), UUID.randomUUID(),
                "Show", "Dallas", "TX", LocalDate.parse("2026-08-20"),
                LocalDate.parse("2026-08-21"), Instant.parse("2026-08-01T00:00:00Z"));
        EventUpdated updated = new EventUpdated(created.eventId(), created.organizerId(),
                "New Show", "Dallas", "TX", created.startDate(), created.endDate(),
                Instant.parse("2026-08-02T00:00:00Z"));
        OverlapFound overlap = overlap();
        OverlapSaved saved = new OverlapSaved(UUID.randomUUID(), overlap.overlapId(),
                overlap.buyerVendorId(), overlap.eventId(), overlap.buyerVendorId(),
                overlap.sellerVendorId(), overlap.cardId(), overlap.score(),
                Instant.parse("2026-08-17T12:01:00Z"));

        send(Topics.EVENT_CREATED, created);
        send(Topics.EVENT_UPDATED, updated);
        String overlapJson = mapper.writeValueAsString(overlap);
        consumer.onFact(new ConsumerRecord<>(Topics.OVERLAP_FOUND, 0, 0, "key", overlapJson));
        send(Topics.OVERLAP_SAVED, saved);

        verify(projections).applyEvent(created);
        verify(projections).applyEvent(updated);
        verify(projections).applyOverlap(overlap, overlapJson);
        verify(projections).applySaved(saved);
    }

    private void send(String topic, Object value) throws Exception {
        consumer.onFact(new ConsumerRecord<>(
                topic, 0, 0, "key", mapper.writeValueAsString(value)));
    }

    private static OverlapFound overlap() {
        return new OverlapFound(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "NM", "LP", 2, 1, new BigDecimal("20.00"),
                new BigDecimal("25.00"), "liquidate", new BigDecimal("91.00"),
                Action.ADDED, Instant.parse("2026-08-17T12:00:00Z"));
    }
}
