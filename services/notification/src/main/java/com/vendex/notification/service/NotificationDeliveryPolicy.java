package com.vendex.notification.service;

import com.vendex.notification.config.NotificationProperties;
import com.vendex.notification.domain.EventSchedule;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.NotificationTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Component
public class NotificationDeliveryPolicy {

    private final NotificationProperties properties;

    public NotificationDeliveryPolicy(NotificationProperties properties) {
        this.properties = properties;
    }

    public Instant availableAt(
            NotificationTrigger trigger, Instant sourceAt,
            NotificationPreferences preferences, EventSchedule event) {
        if (trigger == NotificationTrigger.SAVED_OVERLAP_ACTIVE) {
            return event == null ? null : eventStart(event.startDate());
        }
        return switch (preferences.digestMode()) {
            case REAL_TIME -> sourceAt;
            case DAILY -> nextDigest(sourceAt);
            case EVENT_ONLY -> event == null ? null : eventStart(event.startDate());
        };
    }

    public Instant availableAt(
            NotificationRecord notification,
            NotificationPreferences preferences,
            EventSchedule event) {
        return availableAt(notification.trigger(), notification.sourceOccurredAt(),
                preferences, event);
    }

    public LocalDate today(Instant now) {
        return now.atZone(properties.getEventZone()).toLocalDate();
    }

    public Instant eventStart(LocalDate startDate) {
        return startDate.atStartOfDay(properties.getEventZone()).toInstant();
    }

    private Instant nextDigest(Instant sourceAt) {
        ZonedDateTime source = sourceAt.atZone(properties.getEventZone());
        ZonedDateTime digest = source.toLocalDate()
                .atTime(properties.getDailyDigestTime())
                .atZone(properties.getEventZone());
        if (digest.isBefore(source)) {
            digest = digest.plusDays(1);
        }
        return digest.toInstant();
    }
}
