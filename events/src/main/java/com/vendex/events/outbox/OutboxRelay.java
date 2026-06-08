package com.vendex.events.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Drains the {@code outbox} table to Kafka on a fixed schedule — the "relay"
 * half of the transactional-outbox pattern. The business write committed the
 * event durably; this publishes it afterwards, so a crash between the two can
 * never lose an event (it just delays delivery to the next tick).
 *
 * <p>Delivery is <strong>at-least-once</strong>: a row is marked published only
 * after Kafka acknowledges the send, so a crash after send / before mark
 * re-delivers it. Consumers must therefore be idempotent (a Phase 3 concern).
 *
 * <p>On any send failure the loop stops for this tick rather than skipping
 * ahead — that preserves per-key ordering and lets the row be retried next
 * cycle.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vendex.outbox.poll-interval-ms:1000}")
    public void flush() {
        List<OutboxRecord> batch = repository.fetchUnpublished(properties.getBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxRecord record : batch) {
            try {
                // Block until the broker acks, so we only mark published once the
                // event is durably on the topic.
                kafkaTemplate.send(record.topic(), record.partitionKey(), record.payload()).get();
                repository.markPublished(record.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("outbox relay interrupted; stopping this cycle at row {}", record.id());
                break;
            } catch (Exception e) {
                log.error("outbox publish failed for row {} (topic={}); retrying next cycle",
                        record.id(), record.topic(), e);
                // Preserve ordering: don't advance past a row we couldn't publish.
                break;
            }
        }
    }
}
