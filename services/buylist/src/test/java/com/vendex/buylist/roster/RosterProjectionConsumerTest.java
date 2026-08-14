package com.vendex.buylist.roster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vendex.buylist.repository.BuyListRepository;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RosterProjectionConsumerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private BuyListRepository repository;
    private RosterProjectionConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(BuyListRepository.class);
        consumer = new RosterProjectionConsumer(repository, objectMapper);
    }

    @Test
    void projectsVendorRegistrationAndUnregistration() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        Instant registeredAt = Instant.parse("2026-08-14T12:00:00Z");
        Instant removedAt = registeredAt.plusSeconds(30);
        EventVendorRegistered registered = new EventVendorRegistered(
                eventId, vendorId, registeredAt);
        EventParticipantUnregistered removed = new EventParticipantUnregistered(
                eventId, vendorId, ParticipantRole.VENDOR, removedAt);

        consumer.onRosterEvent(record(Topics.EVENT_VENDOR_REGISTERED,
                objectMapper.writeValueAsString(registered)));
        consumer.onRosterEvent(record(Topics.EVENT_PARTICIPANT_UNREGISTERED,
                objectMapper.writeValueAsString(removed)));

        verify(repository).projectRoster(eventId, vendorId, true, registeredAt);
        verify(repository).projectRoster(eventId, vendorId, false, removedAt);
    }

    @Test
    void ignoresAttendeeUnregistration() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();
        EventParticipantUnregistered removed = new EventParticipantUnregistered(
                eventId, attendeeId, ParticipantRole.ATTENDEE, Instant.now());

        consumer.onRosterEvent(record(Topics.EVENT_PARTICIPANT_UNREGISTERED,
                objectMapper.writeValueAsString(removed)));

        verify(repository, never()).projectRoster(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedEventFailsForKafkaRetry() {
        assertThatThrownBy(() -> consumer.onRosterEvent(
                record(Topics.EVENT_VENDOR_REGISTERED, "not-json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Topics.EVENT_VENDOR_REGISTERED);
    }

    private static ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }
}
