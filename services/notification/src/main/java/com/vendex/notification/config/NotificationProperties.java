package com.vendex.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.time.ZoneId;

@ConfigurationProperties("notification")
public class NotificationProperties {
    private ZoneId eventZone = ZoneId.of("UTC");
    private LocalTime dailyDigestTime = LocalTime.of(9, 0);

    public ZoneId getEventZone() {
        return eventZone;
    }

    public void setEventZone(ZoneId eventZone) {
        this.eventZone = eventZone;
    }

    public LocalTime getDailyDigestTime() {
        return dailyDigestTime;
    }

    public void setDailyDigestTime(LocalTime dailyDigestTime) {
        this.dailyDigestTime = dailyDigestTime;
    }
}
