package com.vendex.events.contract;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the on-the-wire JSON shape of the event contracts: snake_case field
 * names, lowercase action, ISO-8601 dates/timestamps. Uses an ObjectMapper
 * configured the way Spring Boot configures the services' default one
 * (jsr310 registered, dates as strings).
 */
class EventContractSerializationTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void inventoryUpdatedSerializesToSpecShape() throws Exception {
        var event = new InventoryUpdated(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                Action.ADDED,
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"vendor_id\":\"11111111-1111-1111-1111-111111111111\"")
                .contains("\"event_id\":\"22222222-2222-2222-2222-222222222222\"")
                .contains("\"card_id\":\"33333333-3333-3333-3333-333333333333\"")
                .contains("\"action\":\"added\"")
                .contains("\"timestamp\":\"2026-06-08T12:00:00Z\"");
    }

    @Test
    void inventoryUpdatedRoundTrips() throws Exception {
        var event = new InventoryUpdated(UUID.randomUUID(), null, UUID.randomUUID(),
                Action.REMOVED, Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);
        InventoryUpdated back = mapper.readValue(json, InventoryUpdated.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.eventId()).isNull();
    }

    @Test
    void buyListUpdatedHasNoEventId() throws Exception {
        var event = new BuyListUpdated(UUID.randomUUID(), UUID.randomUUID(),
                Action.UPDATED, Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json).doesNotContain("event_id");
        assertThat(json).contains("\"action\":\"updated\"");
    }

    @Test
    void eventCreatedSerializesDatesAsIso() throws Exception {
        var event = new EventCreated(UUID.randomUUID(), UUID.randomUUID(), "Collect-A-Con Dallas",
                "Dallas", "TX", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 19),
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"start_date\":\"2026-07-18\"")
                .contains("\"end_date\":\"2026-07-19\"")
                .contains("\"organizer_id\":");
    }
}
