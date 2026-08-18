package com.vendex.buylist.roster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.buylist.repository.BuyListRepository;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the local event-vendor roster used by the browseable demand view. */
@Component
public class RosterProjectionConsumer {

    private final BuyListRepository repository;
    private final ObjectMapper objectMapper;

    public RosterProjectionConsumer(BuyListRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {
            Topics.EVENT_VENDOR_REGISTERED,
            Topics.EVENT_PARTICIPANT_UNREGISTERED
    })
    @Transactional
    public void onRosterEvent(ConsumerRecord<String, String> record) {
        try {
            if (Topics.EVENT_VENDOR_REGISTERED.equals(record.topic())) {
                EventVendorRegistered event = objectMapper.readValue(
                        record.value(), EventVendorRegistered.class);
                repository.projectRoster(event.eventId(), event.vendorId(), true, event.timestamp());
                return;
            }
            EventParticipantUnregistered event = objectMapper.readValue(
                    record.value(), EventParticipantUnregistered.class);
            if (event.role() == ParticipantRole.VENDOR) {
                repository.projectRoster(event.eventId(), event.userId(), false, event.timestamp());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "invalid roster event on topic " + record.topic(), e);
        }
    }
}
