package com.vendex.events.contract;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "NM", 2, new BigDecimal("14.50"), "normal",
                Action.ADDED,
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"inventory_item_id\":\"11111111-1111-1111-1111-111111111111\"")
                .contains("\"vendor_id\":\"22222222-2222-2222-2222-222222222222\"")
                .contains("\"event_id\":\"33333333-3333-3333-3333-333333333333\"")
                .contains("\"card_id\":\"44444444-4444-4444-4444-444444444444\"")
                .contains("\"asking_price\":14.50")
                .contains("\"action\":\"added\"")
                .contains("\"timestamp\":\"2026-06-08T12:00:00Z\"");
    }

    @Test
    void inventoryUpdatedRoundTrips() throws Exception {
        var event = new InventoryUpdated(UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), "LP", 1, new BigDecimal("10.00"), "liquidate",
                Action.REMOVED, Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);
        InventoryUpdated back = mapper.readValue(json, InventoryUpdated.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.eventId()).isNull();
    }

    @Test
    void buyListUpdatedHasNoEventId() throws Exception {
        var event = new BuyListUpdated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "LP", new BigDecimal("25.00"), 2, Action.UPDATED,
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json).doesNotContain("event_id");
        assertThat(json).contains("\"max_buy_price\":25.00");
        assertThat(json).contains("\"action\":\"updated\"");
    }

    @Test
    void overlapFoundCarriesStableIdentityAndScoreSnapshot() throws Exception {
        var event = new OverlapFound(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "NM", "LP",
                2, 1, new BigDecimal("18.00"), new BigDecimal("20.00"),
                "liquidate", new BigDecimal("91.25"), Action.ADDED,
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);
        OverlapFound back = mapper.readValue(json, OverlapFound.class);

        assertThat(back).isEqualTo(event);
        assertThat(json)
                .contains("\"overlap_id\":")
                .contains("\"buyer_vendor_id\":")
                .contains("\"score\":91.25");
    }

    @Test
    void overlapSavedCarriesEventPlanOwnership() throws Exception {
        var event = new OverlapSaved(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("87.25"),
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readValue(json, OverlapSaved.class)).isEqualTo(event);
        assertThat(json).contains("\"saved_overlap_id\":", "\"event_id\":",
                "\"score\":87.25");
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

    @Test
    void attendeeRegistrationUsesAttendeeIdOnWire() throws Exception {
        var event = new EventAttendeeRegistered(
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event_id\":")
                .contains("\"attendee_id\":")
                .doesNotContain("vendor_id");
    }

    @Test
    void participantUnregisteredSerializesRoleLowercase() throws Exception {
        var event = new EventParticipantUnregistered(
                UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.VENDOR,
                Instant.parse("2026-06-08T12:00:00Z"));

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"user_id\":")
                .contains("\"role\":\"vendor\"");
    }
}
