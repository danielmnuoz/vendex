package com.vendex.events.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final OutboxProperties properties = new OutboxProperties();

    private OutboxRelay relay() {
        return new OutboxRelay(repository, kafkaTemplate, properties);
    }

    private static OutboxRecord row(UUID id, String topic, String key, String payload) {
        return new OutboxRecord(id, "agg", id.toString(), topic, key, payload);
    }

    @Test
    void publishesEachRowThenMarksItPublished() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.fetchUnpublished(properties.getBatchSize())).thenReturn(List.of(
                row(id1, "inventory.updated", "vendorA", "{\"a\":1}"),
                row(id2, "inventory.updated", "vendorB", "{\"b\":2}")));
        when(kafkaTemplate.send(any(String.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay().flush();

        verify(kafkaTemplate).send("inventory.updated", "vendorA", "{\"a\":1}");
        verify(kafkaTemplate).send("inventory.updated", "vendorB", "{\"b\":2}");
        verify(repository).markPublished(id1);
        verify(repository).markPublished(id2);
    }

    @Test
    void stopsAtFirstFailureToPreserveOrdering() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.fetchUnpublished(properties.getBatchSize())).thenReturn(List.of(
                row(id1, "inventory.updated", "vendorA", "{\"a\":1}"),
                row(id2, "inventory.updated", "vendorB", "{\"b\":2}")));
        when(kafkaTemplate.send(eq("inventory.updated"), eq("vendorA"), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay().flush();

        // First row failed → it is not marked, and we never attempt the second.
        verify(repository, never()).markPublished(id1);
        verify(repository, never()).markPublished(id2);
        verify(kafkaTemplate, never()).send(eq("inventory.updated"), eq("vendorB"), any());
    }

    @Test
    void noRowsIsANoOp() {
        when(repository.fetchUnpublished(properties.getBatchSize())).thenReturn(List.of());

        relay().flush();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
