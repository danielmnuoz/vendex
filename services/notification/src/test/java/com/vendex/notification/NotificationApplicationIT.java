package com.vendex.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.EventCreated;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxRelay;
import com.vendex.notification.grpc.NotificationGrpcService;
import com.vendex.notification.v1.GetNotificationsRequest;
import com.vendex.notification.v1.GetNotificationsResponse;
import com.vendex.notification.v1.GetUnreadCountRequest;
import com.vendex.notification.v1.GetUnreadCountResponse;
import com.vendex.notification.v1.InterestStatus;
import com.vendex.notification.v1.ListInterestsForOverlapRequest;
import com.vendex.notification.v1.ListInterestsForOverlapResponse;
import com.vendex.notification.v1.MarkAsReadRequest;
import com.vendex.notification.v1.MarkAsReadResponse;
import com.vendex.notification.v1.NotificationPreferencesResponse;
import com.vendex.notification.v1.NotificationTrigger;
import com.vendex.notification.v1.UpdateNotificationPreferencesRequest;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Kafka source facts -> notification projections/preferences -> gRPC feeds and interests. */
@SpringBootTest
@Testcontainers
class NotificationApplicationIT {

    private static final Instant T0 = Instant.now().minusSeconds(60);

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
                () -> "notification-it-" + UUID.randomUUID());
        registry.add("grpc.server.port", () -> "0");
        registry.add("notification.event-zone", () -> "UTC");
        registry.add("notification.activation-poll-ms", () -> "50");
        registry.add("notification.activation-initial-delay-ms", () -> "3600000");
    }

    @Autowired NotificationGrpcService grpc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext applicationContext;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE overlap_interests, notifications, "
                + "saved_plan_activations, overlap_snapshots, event_schedules, "
                + "notification_preferences");
    }

    @Test
    void overlapLifecycleProducesFeedsPreferencesSavedActivationAndInterest()
            throws Exception {
        assertThat(applicationContext.getBeansOfType(OutboxRelay.class)).isEmpty();

        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID overlapId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        publish(Topics.EVENT_CREATED, eventId, new EventCreated(
                eventId, organizerId, "Card Show", "Baltimore", "MD",
                today, today.plusDays(1), T0));
        awaitCount("event_schedules", 1);

        OverlapFound added = overlap(
                overlapId, eventId, buyerId, sellerId, cardId,
                "87.25", Action.ADDED, T0.plusSeconds(1));
        publish(Topics.OVERLAP_FOUND, overlapId, added);

        GetNotificationsResponse buyer = awaitNotifications(buyerId, eventId, 1);
        GetNotificationsResponse seller = awaitNotifications(sellerId, eventId, 1);
        assertThat(buyer.getNotifications(0).getTrigger())
                .isEqualTo(NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_BUYLIST);
        assertThat(seller.getNotifications(0).getTrigger())
                .isEqualTo(NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_LIQUIDATE);
        assertThat(unread(buyerId, eventId)).isEqualTo(1);

        String buyerNotificationId = buyer.getNotifications(0).getId();
        MarkAsReadResponse readResponse = invoke(observer -> grpc.markAsRead(
                MarkAsReadRequest.newBuilder()
                .setVendorId(buyerId.toString())
                .setNotificationId(buyerNotificationId)
                .build(), observer));
        assertThat(readResponse.getNotification().getRead()).isTrue();
        assertThat(unread(buyerId, eventId)).isZero();

        publish(Topics.OVERLAP_FOUND, overlapId, overlap(
                overlapId, eventId, buyerId, sellerId, cardId,
                "92.00", Action.UPDATED, T0.plusSeconds(2)));
        buyer = awaitUnread(buyerId, eventId, 1);
        assertThat(buyer.getNotifications(0).getId()).isEqualTo(buyerNotificationId);
        assertThat(objectMapper.readTree(buyer.getNotifications(0).getPayloadJson())
                .get("score").decimalValue()).isEqualByComparingTo("92.00");

        NotificationPreferencesResponse muted = invoke(observer ->
                grpc.updateNotificationPreferences(
                UpdateNotificationPreferencesRequest.newBuilder()
                        .setUserId(buyerId.toString())
                        .setReplaceMutedEventIds(true)
                        .addMutedEventIds(eventId.toString())
                        .build(), observer));
        assertThat(muted.getPreferences().getMutedEventIdsList())
                .containsExactly(eventId.toString());
        assertThat(getNotifications(buyerId, eventId).getNotificationsList()).isEmpty();
        assertThat(unread(buyerId, eventId)).isZero();

        NotificationPreferencesResponse unmuted = invoke(observer ->
                grpc.updateNotificationPreferences(
                UpdateNotificationPreferencesRequest.newBuilder()
                        .setUserId(buyerId.toString())
                        .setReplaceMutedEventIds(true)
                        .build(), observer));
        assertThat(unmuted.getPreferences().getMutedEventIdsList()).isEmpty();
        assertThat(awaitNotifications(buyerId, eventId, 1).getNotificationsList())
                .hasSize(1);

        UUID savedOverlapId = UUID.randomUUID();
        publish(Topics.OVERLAP_SAVED, overlapId, new OverlapSaved(
                savedOverlapId, overlapId, buyerId, eventId, buyerId, sellerId,
                cardId, new BigDecimal("92.00"), T0.plusSeconds(3)));

        buyer = awaitNotifications(buyerId, eventId, 2);
        assertThat(buyer.getNotificationsList())
                .extracting(com.vendex.notification.v1.Notification::getTrigger)
                .containsExactlyInAnyOrder(
                        NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_BUYLIST,
                        NotificationTrigger.NOTIFICATION_TRIGGER_SAVED_OVERLAP_ACTIVE);
        ListInterestsForOverlapResponse interests = awaitInterests(
                sellerId, overlapId, 1, InterestStatus.INTEREST_STATUS_PENDING);
        assertThat(interests.getInterests(0).getInterestedVendorId())
                .isEqualTo(buyerId.toString());
        assertThat(interests.getInterests(0).getScore()).isEqualTo("92");

        publish(Topics.OVERLAP_FOUND, overlapId, overlap(
                overlapId, eventId, buyerId, sellerId, cardId,
                "92.00", Action.REMOVED, T0.plusSeconds(4)));
        awaitNotifications(buyerId, eventId, 0);
        awaitNotifications(sellerId, eventId, 0);
        awaitInterests(sellerId, overlapId, 1, InterestStatus.INTEREST_STATUS_EXPIRED);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE active", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM overlap_interests", Integer.class)).isEqualTo(1);
    }

    private void publish(String topic, UUID key, Object value) throws Exception {
        kafka.send(topic, key.toString(), objectMapper.writeValueAsString(value))
                .get(10, TimeUnit.SECONDS);
    }

    private GetNotificationsResponse getNotifications(UUID vendorId, UUID eventId) {
        return invoke(observer -> grpc.getNotifications(GetNotificationsRequest.newBuilder()
                .setVendorId(vendorId.toString())
                .setEventId(eventId.toString())
                .build(), observer));
    }

    private long unread(UUID vendorId, UUID eventId) {
        GetUnreadCountResponse response = invoke(observer -> grpc.getUnreadCount(
                GetUnreadCountRequest.newBuilder()
                        .setVendorId(vendorId.toString())
                        .setEventId(eventId.toString())
                        .build(), observer));
        return response.getUnreadCount();
    }

    private GetNotificationsResponse awaitNotifications(
            UUID vendorId, UUID eventId, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        GetNotificationsResponse last = GetNotificationsResponse.getDefaultInstance();
        while (System.nanoTime() < deadline) {
            last = getNotifications(vendorId, eventId);
            if (last.getNotificationsCount() == expected) {
                return last;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("expected " + expected + " notifications but saw "
                + last.getNotificationsCount());
    }

    private GetNotificationsResponse awaitUnread(
            UUID vendorId, UUID eventId, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            GetNotificationsResponse response = getNotifications(vendorId, eventId);
            if (unread(vendorId, eventId) == expected) {
                return response;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("unread count did not become " + expected);
    }

    private ListInterestsForOverlapResponse awaitInterests(
            UUID sellerId, UUID overlapId, int expected, InterestStatus status)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        ListInterestsForOverlapResponse last =
                ListInterestsForOverlapResponse.getDefaultInstance();
        while (System.nanoTime() < deadline) {
            last = invoke(observer -> grpc.listInterestsForOverlap(
                    ListInterestsForOverlapRequest.newBuilder()
                            .setSellerVendorId(sellerId.toString())
                            .setOverlapId(overlapId.toString())
                            .build(), observer));
            if (last.getInterestsCount() == expected
                    && (expected == 0 || last.getInterests(0).getStatus() == status)) {
                return last;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("interest projection did not reach " + status);
    }

    private void awaitCount(String table, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            if (count != null && count == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(table + " count did not become " + expected);
    }

    private static OverlapFound overlap(
            UUID overlapId, UUID eventId, UUID buyerId, UUID sellerId, UUID cardId,
            String score, Action action, Instant timestamp) {
        return new OverlapFound(overlapId, eventId, buyerId, sellerId, cardId,
                UUID.randomUUID(), UUID.randomUUID(), "NM", "LP", 2, 1,
                new BigDecimal("80.00"), new BigDecimal("100.00"), "liquidate",
                new BigDecimal(score), action, timestamp);
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
