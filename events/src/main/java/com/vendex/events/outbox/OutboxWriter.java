package com.vendex.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Service-facing entry point for emitting a domain event. Serializes the
 * payload to JSON and stores it in the {@code outbox} table via
 * {@link OutboxRepository}.
 *
 * <p>Call this from inside the {@code @Transactional} method that performs the
 * business write. The event becomes durable with the same commit; the
 * {@link OutboxRelay} publishes it to Kafka asynchronously afterwards.
 */
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param aggregateType logical entity type, e.g. {@code "inventory_item"} —
     *                      stored for audit/debugging, not used for routing
     * @param aggregateId   the entity's id, same purpose
     * @param topic         Kafka topic (see {@link com.vendex.events.contract.Topics})
     * @param partitionKey  Kafka message key (typically {@code vendor_id}); may be null
     * @param payload       an event contract record; serialized to JSON
     * @return the generated outbox row id
     */
    public UUID write(String aggregateType, String aggregateId, String topic,
                      String partitionKey, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // A payload we can't serialize is a programming error, not a runtime
            // condition — fail loud so the enclosing transaction rolls back.
            throw new IllegalArgumentException(
                    "failed to serialize outbox payload of type " + payload.getClass().getName(), e);
        }
        return repository.insert(aggregateType, aggregateId, topic, partitionKey, json);
    }
}
