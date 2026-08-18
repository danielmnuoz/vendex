package com.vendex.overlap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import com.vendex.overlap.grpc.OverlapGrpcService;
import com.vendex.overlap.v1.GetOverlapsForVendorRequest;
import com.vendex.overlap.v1.ListOverlapsResponse;
import com.vendex.overlap.v1.ListSavedOverlapsRequest;
import com.vendex.overlap.v1.ListSavedOverlapsResponse;
import com.vendex.overlap.v1.SaveOverlapRequest;
import com.vendex.overlap.v1.SaveOverlapResponse;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Source facts -> Redis SINTER/scoring -> PostgreSQL/outbox -> gRPC + Kafka. */
@SpringBootTest
@Testcontainers
class OverlapApplicationIT {

    private static final Instant T0 = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7-alpine");

    @Container
    static final RedpandaContainer redpanda = new RedpandaContainer(
            DockerImageName.parse("redpandadata/redpanda:v24.2.7"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id",
                () -> "overlap-it-" + UUID.randomUUID());
        registry.add("grpc.server.port", () -> "0");
        registry.add("vendex.outbox.poll-interval-ms", () -> "50");
    }

    @Autowired OverlapGrpcService grpc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE saved_overlaps, overlap_opportunities, outbox");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void factsProduceStableScoredOverlapAndDuplicateSupplyRowsRetireCorrectly()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID bestItemId = UUID.randomUUID();
        UUID fallbackItemId = UUID.randomUUID();
        UUID wantedId = UUID.randomUUID();

        InventoryUpdated fallback = inventory(
                fallbackItemId, sellerId, cardId, "80.00", "normal", Action.ADDED, T0);
        InventoryUpdated best = inventory(
                bestItemId, sellerId, cardId, "90.00", "liquidate", Action.ADDED, T0);
        BuyListUpdated demand = new BuyListUpdated(
                wantedId, buyerId, cardId, "LP", new BigDecimal("100.00"), 2,
                Action.ADDED, T0);

        try (KafkaConsumer<String, String> output = consumer(
                "overlap-output-it-" + UUID.randomUUID());
             KafkaConsumer<String, String> savedOutput = consumer(
                "overlap-saved-output-it-" + UUID.randomUUID())) {
            output.subscribe(List.of(Topics.OVERLAP_FOUND));
            savedOutput.subscribe(List.of(Topics.OVERLAP_SAVED));

            // Publish source facts before roster membership. A later registration
            // must materialize the retained global snapshots and recompute.
            publish(Topics.INVENTORY_UPDATED, sellerId, fallback);
            publish(Topics.BUYLIST_UPDATED, buyerId, demand);
            publish(Topics.INVENTORY_UPDATED, sellerId, best);
            publish(Topics.EVENT_VENDOR_REGISTERED, sellerId,
                    new EventVendorRegistered(eventId, sellerId, T0.plusSeconds(1)));
            publish(Topics.EVENT_VENDOR_REGISTERED, buyerId,
                    new EventVendorRegistered(eventId, buyerId, T0.plusSeconds(1)));

            com.vendex.overlap.v1.Overlap initial = awaitSingleOverlap(buyerId, eventId);
            assertThat(initial.getInventoryItemId()).isEqualTo(bestItemId.toString());
            assertThat(initial.getInventoryPriority()).isEqualTo("liquidate");
            assertThat(new BigDecimal(initial.getScore())).isGreaterThan(new BigDecimal("80"));
            String stableOverlapId = initial.getId();

            SaveOverlapResponse saved = invoke(observer -> grpc.saveOverlap(
                    SaveOverlapRequest.newBuilder()
                            .setVendorId(buyerId.toString())
                            .setOverlapId(stableOverlapId)
                            .build(), observer));
            SaveOverlapResponse savedAgain = invoke(observer -> grpc.saveOverlap(
                    SaveOverlapRequest.newBuilder()
                            .setVendorId(buyerId.toString())
                            .setOverlapId(stableOverlapId)
                            .build(), observer));
            assertThat(savedAgain.getSavedOverlap().getId())
                    .isEqualTo(saved.getSavedOverlap().getId());
            assertThat(awaitSavedEvents(savedOutput, saved.getSavedOverlap().getId(),
                    stableOverlapId)).isEqualTo(1);

            publish(Topics.INVENTORY_UPDATED, sellerId,
                    inventory(bestItemId, sellerId, cardId, "90.00", "liquidate",
                            Action.REMOVED, T0.plusSeconds(2)));
            com.vendex.overlap.v1.Overlap fallbackSelected = awaitOverlapItem(
                    buyerId, eventId, fallbackItemId);
            assertThat(fallbackSelected.getId()).isEqualTo(stableOverlapId);

            publish(Topics.INVENTORY_UPDATED, sellerId,
                    inventory(fallbackItemId, sellerId, cardId, "80.00", "normal",
                            Action.REMOVED, T0.plusSeconds(3)));
            awaitNoOverlaps(buyerId, eventId);

            EnumSet<Action> actions = awaitActions(output, stableOverlapId);
            assertThat(actions).contains(Action.ADDED, Action.UPDATED, Action.REMOVED);

            ListSavedOverlapsResponse savedAfterRetirement = invoke(observer ->
                    grpc.listSavedOverlaps(ListSavedOverlapsRequest.newBuilder()
                            .setVendorId(buyerId.toString())
                            .setEventId(eventId.toString())
                            .build(), observer));
            assertThat(savedAfterRetirement.getSavedOverlapsList()).singleElement()
                    .satisfies(value -> {
                        assertThat(value.getOverlap().getId()).isEqualTo(stableOverlapId);
                        assertThat(value.getOverlap().getActive()).isFalse();
                    });

            // Newer supply reactivates the same deterministic ID; leaving the
            // event retires it without deleting the saved historical plan.
            publish(Topics.INVENTORY_UPDATED, sellerId,
                    inventory(fallbackItemId, sellerId, cardId, "75.00", "normal",
                            Action.ADDED, T0.plusSeconds(4)));
            assertThat(awaitSingleOverlap(buyerId, eventId).getId())
                    .isEqualTo(stableOverlapId);
            publish(Topics.EVENT_PARTICIPANT_UNREGISTERED, buyerId,
                    new EventParticipantUnregistered(eventId, buyerId,
                            ParticipantRole.VENDOR, T0.plusSeconds(5)));
            awaitNoOverlaps(buyerId, eventId);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM overlap_opportunities WHERE active", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saved_overlaps", Integer.class))
                .isEqualTo(1);
        awaitOutboxDrained();
    }

    private void publish(String topic, UUID key, Object value) throws Exception {
        kafka.send(topic, key.toString(), objectMapper.writeValueAsString(value))
                .get(10, TimeUnit.SECONDS);
    }

    private com.vendex.overlap.v1.Overlap awaitSingleOverlap(UUID vendorId, UUID eventId)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            ListOverlapsResponse response = get(vendorId, eventId);
            if (response.getOverlapsCount() == 1) {
                return response.getOverlaps(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("overlap was not materialized");
    }

    private com.vendex.overlap.v1.Overlap awaitOverlapItem(
            UUID vendorId, UUID eventId, UUID itemId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            ListOverlapsResponse response = get(vendorId, eventId);
            if (response.getOverlapsCount() == 1
                    && response.getOverlaps(0).getInventoryItemId().equals(itemId.toString())) {
                return response.getOverlaps(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("overlap did not select item " + itemId);
    }

    private void awaitNoOverlaps(UUID vendorId, UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (get(vendorId, eventId).getOverlapsCount() == 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("overlap was not retired");
    }

    private ListOverlapsResponse get(UUID vendorId, UUID eventId) {
        return invoke(observer -> grpc.getOverlapsForVendor(
                GetOverlapsForVendorRequest.newBuilder()
                        .setVendorId(vendorId.toString())
                        .setEventId(eventId.toString())
                        .build(), observer));
    }

    private EnumSet<Action> awaitActions(
            KafkaConsumer<String, String> consumer, String overlapId) throws Exception {
        EnumSet<Action> actions = EnumSet.noneOf(Action.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline
                && !actions.containsAll(EnumSet.allOf(Action.class))) {
            consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                try {
                    OverlapFound event = objectMapper.readValue(record.value(), OverlapFound.class);
                    if (event.overlapId().toString().equals(overlapId)) {
                        actions.add(event.action());
                    }
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
        }
        return actions;
    }

    private int awaitSavedEvents(
            KafkaConsumer<String, String> consumer, String savedId, String overlapId)
            throws Exception {
        int matches = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline && matches == 0) {
            for (var record : consumer.poll(Duration.ofMillis(500))) {
                OverlapSaved event = objectMapper.readValue(record.value(), OverlapSaved.class);
                if (event.savedOverlapId().toString().equals(savedId)
                        && event.overlapId().toString().equals(overlapId)) {
                    matches++;
                }
            }
        }
        for (var record : consumer.poll(Duration.ofSeconds(1))) {
            OverlapSaved event = objectMapper.readValue(record.value(), OverlapSaved.class);
            if (event.savedOverlapId().toString().equals(savedId)
                    && event.overlapId().toString().equals(overlapId)) {
                matches++;
            }
        }
        return matches;
    }

    private void awaitOutboxDrained() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class);
            if (count != null && count == 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("overlap outbox did not drain");
    }

    private static InventoryUpdated inventory(
            UUID itemId, UUID vendorId, UUID cardId, String askingPrice,
            String priority, Action action, Instant timestamp) {
        return new InventoryUpdated(itemId, vendorId, null, cardId, "NM", 2,
                new BigDecimal(askingPrice), priority, action, timestamp);
    }

    private static KafkaConsumer<String, String> consumer(String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static <T> T invoke(java.util.function.Consumer<StreamObserver<T>> call) {
        ArrayList<T> values = new ArrayList<>();
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
