package com.vendex.inventory;

import com.vendex.events.contract.Topics;
import com.vendex.inventory.catalog.CardCatalogGateway;
import com.vendex.inventory.grpc.InventoryGrpcService;
import com.vendex.inventory.v1.AddInventoryRequest;
import com.vendex.inventory.v1.AddInventoryResponse;
import com.vendex.inventory.v1.CardCondition;
import com.vendex.inventory.v1.InventoryPriority;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** PostgreSQL business write + outbox commit + real Redpanda publication. */
@SpringBootTest
@Testcontainers
class InventoryApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RedpandaContainer redpanda = new RedpandaContainer(
            DockerImageName.parse("redpandadata/redpanda:v24.2.7"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
        registry.add("grpc.server.port", () -> "0");
        registry.add("vendex.outbox.poll-interval-ms", () -> "50");
    }

    @Autowired InventoryGrpcService grpc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CardCatalogGateway cards;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE outbox, inventory_items RESTART IDENTITY CASCADE");
    }

    @Test
    void addPersistsCanonicalInventoryAndPublishesThroughTheOutbox() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(cards.get(cardId)).thenReturn(new CardCatalogGateway.CanonicalCard(
                cardId, "sv03-001", "Pikachu", "sv03", "Obsidian Flames"));

        try (KafkaConsumer<String, String> consumer = consumer("inventory-it-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(Topics.INVENTORY_UPDATED));

            AddInventoryResponse response = invoke(observer -> grpc.addInventory(
                    AddInventoryRequest.newBuilder()
                            .setVendorId(vendorId.toString())
                            .setCardId(cardId.toString())
                            .setCondition(CardCondition.CARD_CONDITION_NM)
                            .setQuantity(2)
                            .setAskingPrice("19.99")
                            .setPriority(InventoryPriority.INVENTORY_PRIORITY_NORMAL)
                            .build(), observer));

            assertThat(response.getItem().getCardId()).isEqualTo(cardId.toString());
            assertThat(response.getItem().getAskingPrice()).isEqualTo("19.99");

            var received = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }

            assertThat(received).singleElement().satisfies(record -> {
                assertThat(record.topic()).isEqualTo(Topics.INVENTORY_UPDATED);
                assertThat(record.key()).isEqualTo(vendorId.toString());
                assertThat(record.value()).contains(
                        vendorId.toString(), cardId.toString(), "\"action\": \"added\"");
            });
        }

        Integer itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_items", Integer.class);
        Integer unpublishedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class);
        assertThat(itemCount).isEqualTo(1);
        assertThat(unpublishedCount).isZero();
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
