package com.vendex.events.outbox;

import java.util.UUID;

/**
 * One unpublished row from the {@code outbox} table, as the relay reads it.
 * {@code payload} is the JSON string to put on the wire (stored as {@code
 * jsonb}, read back as text); {@code partitionKey} becomes the Kafka message
 * key (may be null).
 */
public record OutboxRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String topic,
        String partitionKey,
        String payload
) {}
