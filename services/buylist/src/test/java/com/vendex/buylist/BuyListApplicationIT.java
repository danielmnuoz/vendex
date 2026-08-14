package com.vendex.buylist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.buylist.catalog.CardCatalogGateway;
import com.vendex.buylist.grpc.BuyListGrpcService;
import com.vendex.buylist.v1.AddWantedCardRequest;
import com.vendex.buylist.v1.AddWantedCardResponse;
import com.vendex.buylist.v1.CardCondition;
import com.vendex.buylist.v1.ListBuyListsForEventRequest;
import com.vendex.buylist.v1.ListWantedCardsResponse;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Roster fact -> local view plus buy-list write -> outbox -> Redpanda. */
@SpringBootTest
@Testcontainers
class BuyListApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RedpandaContainer redpanda = new RedpandaContainer(
            DockerImageName.parse("redpandadata/redpanda:v24.2.7"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id",
                () -> "buylist-roster-it-" + UUID.randomUUID());
        registry.add("grpc.server.port", () -> "0");
        registry.add("vendex.outbox.poll-interval-ms", () -> "50");
    }

    @Autowired BuyListGrpcService grpc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CardCatalogGateway cards;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE outbox, wanted_cards, event_vendor_roster RESTART IDENTITY CASCADE");
    }

    @Test
    void registeredVendorDemandIsBrowseableAndPublishesThroughOutbox() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant registeredAt = Instant.parse("2026-08-14T12:00:00Z");
        when(cards.get(cardId)).thenReturn(new CardCatalogGateway.CanonicalCard(
                cardId, "sv03-001", "Pikachu", "sv03", "Obsidian Flames"));

        kafka.send(Topics.EVENT_VENDOR_REGISTERED, vendorId.toString(),
                objectMapper.writeValueAsString(new EventVendorRegistered(
                        eventId, vendorId, registeredAt))).get(10, TimeUnit.SECONDS);
        awaitRoster(eventId, vendorId, true);

        try (KafkaConsumer<String, String> consumer = consumer("buylist-it-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(Topics.BUYLIST_UPDATED));
            AddWantedCardResponse added = invoke(observer -> grpc.addWantedCard(
                    AddWantedCardRequest.newBuilder()
                            .setVendorId(vendorId.toString())
                            .setCardId(cardId.toString())
                            .setMinimumCondition(CardCondition.CARD_CONDITION_LP)
                            .setMaxBuyPrice("25.00")
                            .setQuantityWanted(2)
                            .build(), observer));
            assertThat(added.getWantedCard().getMaxBuyPrice()).isEqualTo("25");

            ListWantedCardsResponse browse = invoke(observer -> grpc.listBuyListsForEvent(
                    ListBuyListsForEventRequest.newBuilder()
                            .setEventId(eventId.toString())
                            .build(), observer));
            assertThat(browse.getWantedCardsList()).singleElement().satisfies(wanted -> {
                assertThat(wanted.getVendorId()).isEqualTo(vendorId.toString());
                assertThat(wanted.getCardId()).isEqualTo(cardId.toString());
            });

            var received = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
            assertThat(received).singleElement().satisfies(record -> {
                assertThat(record.key()).isEqualTo(vendorId.toString());
                assertThat(record.value()).contains(
                        vendorId.toString(), cardId.toString(), "\"action\": \"added\"");
            });
        }

        kafka.send(Topics.EVENT_PARTICIPANT_UNREGISTERED, vendorId.toString(),
                objectMapper.writeValueAsString(new EventParticipantUnregistered(
                        eventId, vendorId, ParticipantRole.VENDOR,
                        registeredAt.plusSeconds(30)))).get(10, TimeUnit.SECONDS);
        awaitRoster(eventId, vendorId, false);
        ListWantedCardsResponse afterRemoval = invoke(observer -> grpc.listBuyListsForEvent(
                ListBuyListsForEventRequest.newBuilder()
                        .setEventId(eventId.toString())
                        .build(), observer));
        assertThat(afterRemoval.getWantedCardsList()).isEmpty();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wanted_cards", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class))
                .isZero();
    }

    private void awaitRoster(UUID eventId, UUID vendorId, boolean active) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM event_vendor_roster WHERE event_id = ? AND vendor_id = ? AND active = ?",
                    Integer.class, eventId, vendorId, active);
            if (count != null && count == 1) return;
            Thread.sleep(100);
        }
        throw new AssertionError("roster projection did not reach active=" + active);
    }

    private static KafkaConsumer<String, String> consumer(String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static <T> T invoke(java.util.function.Consumer<StreamObserver<T>> call) {
        java.util.ArrayList<T> values = new java.util.ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        call.accept(new StreamObserver<>() {
            @Override public void onNext(T value) { values.add(value); }
            @Override public void onError(Throwable value) { error.set(value); }
            @Override public void onCompleted() {}
        });
        if (error.get() != null) {
            throw new AssertionError("unexpected gRPC error", error.get());
        }
        return values.getFirst();
    }
}
