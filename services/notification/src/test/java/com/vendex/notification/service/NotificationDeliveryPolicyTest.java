package com.vendex.notification.service;

import com.vendex.notification.config.NotificationProperties;
import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.EventSchedule;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryPolicyTest {

    private NotificationDeliveryPolicy policy;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEventZone(ZoneId.of("UTC"));
        properties.setDailyDigestTime(LocalTime.of(9, 0));
        policy = new NotificationDeliveryPolicy(properties);
    }

    @Test
    void realTimeAndDailyHaveDeterministicAvailability() {
        Instant beforeDigest = Instant.parse("2026-08-17T08:00:00Z");
        Instant afterDigest = Instant.parse("2026-08-17T10:00:00Z");

        assertThat(policy.availableAt(NotificationTrigger.OVERLAP_BUYLIST,
                beforeDigest, preferences(DigestMode.REAL_TIME), null))
                .isEqualTo(beforeDigest);
        assertThat(policy.availableAt(NotificationTrigger.OVERLAP_BUYLIST,
                beforeDigest, preferences(DigestMode.DAILY), null))
                .isEqualTo(Instant.parse("2026-08-17T09:00:00Z"));
        assertThat(policy.availableAt(NotificationTrigger.OVERLAP_BUYLIST,
                afterDigest, preferences(DigestMode.DAILY), null))
                .isEqualTo(Instant.parse("2026-08-18T09:00:00Z"));
    }

    @Test
    void eventOnlyAndSavedPlanWaitForEventDate() {
        EventSchedule event = new EventSchedule(UUID.randomUUID(), "Show",
                LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-21"),
                Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(policy.availableAt(NotificationTrigger.OVERLAP_BUYLIST,
                Instant.parse("2026-08-17T12:00:00Z"),
                preferences(DigestMode.EVENT_ONLY), event))
                .isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
        assertThat(policy.availableAt(NotificationTrigger.SAVED_OVERLAP_ACTIVE,
                Instant.parse("2026-08-17T12:00:00Z"),
                preferences(DigestMode.REAL_TIME), event))
                .isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
    }

    @Test
    void eventOnlyRemainsUnavailableUntilScheduleProjectionArrives() {
        assertThat(policy.availableAt(NotificationTrigger.OVERLAP_BUYLIST,
                Instant.parse("2026-08-17T12:00:00Z"),
                preferences(DigestMode.EVENT_ONLY), null)).isNull();
    }

    private static NotificationPreferences preferences(DigestMode digest) {
        return new NotificationPreferences(UUID.randomUUID(), true, false,
                true, true, true, digest, Set.of(), Instant.EPOCH);
    }
}
