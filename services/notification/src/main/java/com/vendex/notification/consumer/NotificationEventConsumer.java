package com.vendex.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.EventCreated;
import com.vendex.events.contract.EventUpdated;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.Topics;
import com.vendex.notification.service.NotificationProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationProjectionService projections;

    public NotificationEventConsumer(
            ObjectMapper objectMapper, NotificationProjectionService projections) {
        this.objectMapper = objectMapper;
        this.projections = projections;
    }

    @KafkaListener(topics = {
            Topics.OVERLAP_FOUND,
            Topics.OVERLAP_SAVED,
            Topics.EVENT_CREATED,
            Topics.EVENT_UPDATED
    })
    public void onFact(ConsumerRecord<String, String> record) {
        try {
            switch (record.topic()) {
                case Topics.OVERLAP_FOUND -> projections.applyOverlap(
                        objectMapper.readValue(record.value(), OverlapFound.class),
                        record.value());
                case Topics.OVERLAP_SAVED -> projections.applySaved(
                        objectMapper.readValue(record.value(), OverlapSaved.class));
                case Topics.EVENT_CREATED -> projections.applyEvent(
                        objectMapper.readValue(record.value(), EventCreated.class));
                case Topics.EVENT_UPDATED -> projections.applyEvent(
                        objectMapper.readValue(record.value(), EventUpdated.class));
                default -> throw new IllegalArgumentException(
                        "unsupported notification topic " + record.topic());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "invalid notification source event on topic " + record.topic(), e);
        }
    }
}
