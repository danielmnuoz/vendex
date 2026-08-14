package com.vendex.event;

import com.vendex.event.grpc.EventGrpcService;
import com.vendex.event.v1.CreateEventRequest;
import com.vendex.event.v1.CreateEventResponse;
import com.vendex.event.v1.RegisterForEventRequest;
import com.vendex.event.v1.RegisterForEventResponse;
import com.vendex.event.v1.RegistrationRole;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full producer round trip: Spring + Flyway + PostgreSQL business write and
 * outbox insert + scheduled relay + a real Redpanda broker.
 */
@SpringBootTest
@Testcontainers
class EventApplicationIT {

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

    @Autowired EventGrpcService grpc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE outbox, event_registrations, events RESTART IDENTITY CASCADE");
    }

    @Test
    void eventAndVendorRegistrationPersistAndPublishThroughOutbox() {
        UUID organizerId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = consumer("event-service-it-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(Topics.EVENT_CREATED, Topics.EVENT_VENDOR_REGISTERED));

            CreateEventResponse created = invoke(obs -> grpc.createEvent(
                    CreateEventRequest.newBuilder()
                            .setOrganizerId(organizerId.toString())
                            .setName("Collect-A-Con Dallas")
                            .setCity("Dallas")
                            .setState("TX")
                            .setVenue("Convention Center")
                            .setStartDate(LocalDate.of(2026, 10, 3).toString())
                            .setEndDate(LocalDate.of(2026, 10, 4).toString())
                            .setDescription("Pokemon weekend")
                            .build(), obs));
            UUID eventId = UUID.fromString(created.getEvent().getId());
            RegisterForEventResponse registered = invoke(obs -> grpc.registerForEvent(
                    RegisterForEventRequest.newBuilder()
                            .setEventId(eventId.toString())
                            .setUserId(vendorId.toString())
                            .setRole(RegistrationRole.REGISTRATION_ROLE_VENDOR)
                            .setBooth("B-7")
                            .build(), obs));
            assertThat(registered.getRegistration().getBooth()).isEqualTo("B-7");

            var received = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.size() < 2 && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }

            assertThat(received).extracting(record -> record.topic())
                    .contains(Topics.EVENT_CREATED, Topics.EVENT_VENDOR_REGISTERED);
            assertThat(received)
                    .filteredOn(record -> record.topic().equals(Topics.EVENT_CREATED))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record.value()).contains(eventId.toString(), "Collect-A-Con Dallas");
                    });
            assertThat(received)
                    .filteredOn(record -> record.topic().equals(Topics.EVENT_VENDOR_REGISTERED))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record.key()).isEqualTo(vendorId.toString());
                        assertThat(record.value()).contains(eventId.toString(), vendorId.toString());
                    });
        }

        Integer eventCount = jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class);
        Integer registrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_registrations", Integer.class);
        Integer unpublishedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL", Integer.class);
        assertThat(eventCount).isEqualTo(1);
        assertThat(registrationCount).isEqualTo(1);
        assertThat(unpublishedCount).isZero();
    }

    private static KafkaConsumer<String, String> consumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    private static <T> T invoke(java.util.function.Consumer<StreamObserver<T>> call) {
        java.util.ArrayList<T> out = new java.util.ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        call.accept(new StreamObserver<>() {
            @Override public void onNext(T value) { out.add(value); }
            @Override public void onError(Throwable t) { error.set(t); }
            @Override public void onCompleted() {}
        });
        if (error.get() != null) {
            throw new AssertionError("unexpected gRPC error", error.get());
        }
        return out.get(0);
    }
}
