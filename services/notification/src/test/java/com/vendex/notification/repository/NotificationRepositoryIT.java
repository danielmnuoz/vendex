package com.vendex.notification.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.EventSchedule;
import com.vendex.notification.domain.InterestStatus;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationTrigger;
import com.vendex.notification.domain.OverlapProjection;
import com.vendex.notification.domain.SavedPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers
@Import(NotificationRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        JacksonAutoConfiguration.class})
class NotificationRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired NotificationRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE overlap_interests, notifications, "
                + "saved_plan_activations, overlap_snapshots, event_schedules, "
                + "notification_preferences");
    }

    @Test
    void notificationUpsertDeduplicatesAndUnchangedReplayKeepsReadState() {
        UUID vendor = UUID.randomUUID();
        OverlapProjection overlap = overlap(true, 1, NOW);
        repository.upsertOverlap(overlap);
        repository.getOrCreatePreferences(vendor, NOW);
        var first = repository.upsertNotification(
                vendor, overlap.eventId(), NotificationTrigger.OVERLAP_BUYLIST,
                overlap.overlapId(), overlap.cardId(), overlap.sellerVendorId(),
                overlap.payloadJson(), NOW, NOW, NOW);
        repository.markRead(vendor, first.id(), NOW.plusSeconds(1));

        var replay = repository.upsertNotification(
                vendor, overlap.eventId(), NotificationTrigger.OVERLAP_BUYLIST,
                overlap.overlapId(), overlap.cardId(), overlap.sellerVendorId(),
                overlap.payloadJson(), NOW, NOW, NOW.plusSeconds(2));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.read()).isTrue();
        assertThat(repository.listVisible(vendor, null, NOW.plusSeconds(3), 10, 0))
                .singleElement().satisfies(value -> assertThat(value.read()).isTrue());
        assertThat(repository.unreadCount(vendor, null, NOW.plusSeconds(3))).isZero();

        var changed = repository.upsertNotification(
                vendor, overlap.eventId(), NotificationTrigger.OVERLAP_BUYLIST,
                overlap.overlapId(), overlap.cardId(), overlap.sellerVendorId(),
                "{\"score\":92}", NOW.plusSeconds(4), NOW, NOW.plusSeconds(4));
        assertThat(changed.id()).isEqualTo(first.id());
        assertThat(changed.read()).isFalse();
    }

    @Test
    void preferencesDynamicallyHideMutedAndDisabledNotifications() {
        UUID vendor = UUID.randomUUID();
        OverlapProjection overlap = overlap(true, 1, NOW);
        repository.getOrCreatePreferences(vendor, NOW);
        repository.upsertNotification(vendor, overlap.eventId(),
                NotificationTrigger.OVERLAP_BUYLIST, overlap.overlapId(), overlap.cardId(),
                overlap.sellerVendorId(), overlap.payloadJson(), NOW, NOW, NOW);

        repository.savePreferences(new NotificationPreferences(vendor, true, false,
                true, true, true, DigestMode.REAL_TIME, Set.of(overlap.eventId()),
                NOW.plusSeconds(1)));
        assertThat(repository.listVisible(vendor, null, NOW.plusSeconds(2), 10, 0)).isEmpty();

        repository.savePreferences(new NotificationPreferences(vendor, true, false,
                false, true, true, DigestMode.REAL_TIME, Set.of(), NOW.plusSeconds(3)));
        assertThat(repository.listVisible(vendor, null, NOW.plusSeconds(4), 10, 0)).isEmpty();

        repository.savePreferences(new NotificationPreferences(vendor, true, false,
                true, true, true, DigestMode.REAL_TIME, Set.of(), NOW.plusSeconds(5)));
        assertThat(repository.listVisible(vendor, overlap.eventId(),
                NOW.plusSeconds(6), 10, 0)).hasSize(1);
    }

    @Test
    void savedPlanCreatesRankedInterestAndBecomesDueOnEventDate() {
        OverlapProjection overlap = repository.upsertOverlap(overlap(true, 1, NOW));
        repository.upsertEvent(new EventSchedule(overlap.eventId(), "Show",
                LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-18"), NOW));
        SavedPlan saved = repository.upsertSavedPlan(new SavedPlan(
                UUID.randomUUID(), overlap.overlapId(), overlap.buyerVendorId(),
                overlap.eventId(), overlap.buyerVendorId(), overlap.sellerVendorId(),
                overlap.cardId(), overlap.score(), NOW.plusSeconds(1), null));
        var interest = repository.upsertInterest(saved, NOW.plusSeconds(1));

        assertThat(interest.counterpartyVendorId()).isEqualTo(overlap.sellerVendorId());
        assertThat(interest.status()).isEqualTo(InterestStatus.PENDING);
        assertThat(repository.listInterests(
                overlap.sellerVendorId(), overlap.overlapId(), 10, 0))
                .extracting(value -> value.id()).containsExactly(interest.id());
        assertThat(repository.dueSavedPlans(LocalDate.parse("2026-08-17")))
                .extracting(SavedPlan::savedOverlapId).containsExactly(saved.savedOverlapId());

        repository.expirePendingInterests(overlap.overlapId(), NOW.plusSeconds(2));
        assertThat(repository.findInterest(overlap.overlapId(), overlap.buyerVendorId()))
                .get().extracting(value -> value.status()).isEqualTo(InterestStatus.EXPIRED);
        repository.restoreSavedInterests(overlap.overlapId(), NOW.plusSeconds(3));
        assertThat(repository.findInterest(overlap.overlapId(), overlap.buyerVendorId()))
                .get().extracting(value -> value.status()).isEqualTo(InterestStatus.PENDING);
    }

    @Test
    void removalWinsOlderAndEqualTimestampReactivation() {
        OverlapProjection active = overlap(true, 1, NOW);
        repository.upsertOverlap(active);
        OverlapProjection removed = overlap(active, false, 3, NOW.plusSeconds(1));
        repository.upsertOverlap(removed);

        assertThat(repository.upsertOverlap(overlap(active, true, 1, NOW)).active()).isFalse();
        assertThat(repository.upsertOverlap(
                overlap(active, true, 1, NOW.plusSeconds(1))).active()).isFalse();
    }

    private static OverlapProjection overlap(boolean active, int rank, Instant occurredAt) {
        return new OverlapProjection(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "liquidate", new BigDecimal("91.00"),
                "{\"score\":91}", active, rank, occurredAt);
    }

    private static OverlapProjection overlap(
            OverlapProjection value, boolean active, int rank, Instant occurredAt) {
        return new OverlapProjection(value.overlapId(), value.eventId(), value.buyerVendorId(),
                value.sellerVendorId(), value.cardId(), value.inventoryPriority(), value.score(),
                value.payloadJson(), active, rank, occurredAt);
    }
}
