package com.vendex.events.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.Topics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Test
    void writeSerializesPayloadAndInsertsRow() {
        OutboxWriter writer = new OutboxWriter(repository, objectMapper);
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        var payload = new BuyListUpdated(vendorId, cardId, Action.ADDED,
                Instant.parse("2026-06-08T12:00:00Z"));
        when(repository.insert(eq("wanted_card"), eq(cardId.toString()), eq(Topics.BUYLIST_UPDATED),
                eq(vendorId.toString()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(UUID.randomUUID());

        writer.write("wanted_card", cardId.toString(), Topics.BUYLIST_UPDATED,
                vendorId.toString(), payload);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq("wanted_card"), eq(cardId.toString()), eq(Topics.BUYLIST_UPDATED),
                eq(vendorId.toString()), json.capture());
        assertThat(json.getValue())
                .contains("\"vendor_id\":\"" + vendorId + "\"")
                .contains("\"action\":\"added\"");
    }
}
