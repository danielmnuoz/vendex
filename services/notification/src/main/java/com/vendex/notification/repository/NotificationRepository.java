package com.vendex.notification.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.EventSchedule;
import com.vendex.notification.domain.InterestStatus;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.NotificationTrigger;
import com.vendex.notification.domain.OverlapInterest;
import com.vendex.notification.domain.OverlapProjection;
import com.vendex.notification.domain.SavedPlan;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class NotificationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public NotificationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public EventSchedule upsertEvent(EventSchedule event) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO event_schedules (
                        event_id, name, start_date, end_date, occurred_at)
                    VALUES (:event_id, :name, :start_date, :end_date, :occurred_at)
                    ON CONFLICT (event_id) DO UPDATE SET
                        name = EXCLUDED.name,
                        start_date = EXCLUDED.start_date,
                        end_date = EXCLUDED.end_date,
                        occurred_at = EXCLUDED.occurred_at
                    WHERE EXCLUDED.occurred_at >= event_schedules.occurred_at
                    RETURNING *
                    """, new MapSqlParameterSource()
                    .addValue("event_id", event.eventId())
                    .addValue("name", event.name())
                    .addValue("start_date", event.startDate())
                    .addValue("end_date", event.endDate())
                    .addValue("occurred_at", Timestamp.from(event.occurredAt())),
                    this::mapEvent);
        } catch (EmptyResultDataAccessException e) {
            return findEvent(event.eventId()).orElseThrow();
        }
    }

    public Optional<EventSchedule> findEvent(UUID eventId) {
        return optional("SELECT * FROM event_schedules WHERE event_id = :event_id",
                new MapSqlParameterSource("event_id", eventId), this::mapEvent);
    }

    public OverlapProjection upsertOverlap(OverlapProjection overlap) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO overlap_snapshots (
                        overlap_id, event_id, buyer_vendor_id, seller_vendor_id,
                        card_id, inventory_priority, score, payload, active,
                        action_rank, occurred_at)
                    VALUES (
                        :overlap_id, :event_id, :buyer_id, :seller_id,
                        :card_id, :priority, :score, CAST(:payload AS jsonb), :active,
                        :action_rank, :occurred_at)
                    ON CONFLICT (overlap_id) DO UPDATE SET
                        event_id = EXCLUDED.event_id,
                        buyer_vendor_id = EXCLUDED.buyer_vendor_id,
                        seller_vendor_id = EXCLUDED.seller_vendor_id,
                        card_id = EXCLUDED.card_id,
                        inventory_priority = EXCLUDED.inventory_priority,
                        score = EXCLUDED.score,
                        payload = EXCLUDED.payload,
                        active = EXCLUDED.active,
                        action_rank = EXCLUDED.action_rank,
                        occurred_at = EXCLUDED.occurred_at
                    WHERE EXCLUDED.occurred_at > overlap_snapshots.occurred_at
                       OR (EXCLUDED.occurred_at = overlap_snapshots.occurred_at
                           AND EXCLUDED.action_rank >= overlap_snapshots.action_rank)
                    RETURNING *
                    """, overlapParams(overlap), this::mapOverlap);
        } catch (EmptyResultDataAccessException e) {
            return findOverlap(overlap.overlapId()).orElseThrow();
        }
    }

    public Optional<OverlapProjection> findOverlap(UUID overlapId) {
        return optional("SELECT * FROM overlap_snapshots WHERE overlap_id = :overlap_id",
                new MapSqlParameterSource("overlap_id", overlapId), this::mapOverlap);
    }

    public SavedPlan upsertSavedPlan(SavedPlan saved) {
        jdbc.update("""
                INSERT INTO saved_plan_activations (
                    saved_overlap_id, overlap_id, vendor_id, event_id,
                    buyer_vendor_id, seller_vendor_id, card_id, score, saved_at)
                VALUES (
                    :saved_id, :overlap_id, :vendor_id, :event_id,
                    :buyer_id, :seller_id, :card_id, :score, :saved_at)
                ON CONFLICT (vendor_id, overlap_id) DO NOTHING
                """, savedParams(saved));
        return findSavedPlan(saved.vendorId(), saved.overlapId()).orElseThrow();
    }

    public Optional<SavedPlan> findSavedPlan(UUID vendorId, UUID overlapId) {
        return optional("""
                SELECT * FROM saved_plan_activations
                WHERE vendor_id = :vendor_id AND overlap_id = :overlap_id
                """, new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("overlap_id", overlapId), this::mapSavedPlan);
    }

    public List<SavedPlan> savedPlansForOverlap(UUID overlapId) {
        return jdbc.query("""
                SELECT * FROM saved_plan_activations
                WHERE overlap_id = :overlap_id
                ORDER BY saved_at, saved_overlap_id
                """, new MapSqlParameterSource("overlap_id", overlapId), this::mapSavedPlan);
    }

    public List<SavedPlan> dueSavedPlans(LocalDate today) {
        return jdbc.query("""
                SELECT s.*
                FROM saved_plan_activations s
                JOIN event_schedules e ON e.event_id = s.event_id
                JOIN overlap_snapshots o ON o.overlap_id = s.overlap_id AND o.active
                WHERE e.start_date <= :today
                ORDER BY e.start_date, s.saved_at, s.saved_overlap_id
                """, new MapSqlParameterSource("today", today), this::mapSavedPlan);
    }

    public void markSavedPlanActivated(UUID savedOverlapId, Instant now) {
        jdbc.update("""
                UPDATE saved_plan_activations
                SET activated_at = COALESCE(activated_at, :now)
                WHERE saved_overlap_id = :saved_id
                """, new MapSqlParameterSource("saved_id", savedOverlapId)
                .addValue("now", Timestamp.from(now)));
    }

    public OverlapInterest upsertInterest(SavedPlan saved, Instant now) {
        UUID counterparty = saved.counterpartyVendorId();
        jdbc.update("""
                INSERT INTO overlap_interests (
                    overlap_id, interested_vendor_id, counterparty_vendor_id,
                    event_id, score, status, created_at, updated_at)
                VALUES (
                    :overlap_id, :interested_id, :counterparty_id,
                    :event_id, :score, 'pending', :now, :now)
                ON CONFLICT (overlap_id, interested_vendor_id) DO NOTHING
                """, new MapSqlParameterSource("overlap_id", saved.overlapId())
                .addValue("interested_id", saved.vendorId())
                .addValue("counterparty_id", counterparty)
                .addValue("event_id", saved.eventId())
                .addValue("score", saved.score())
                .addValue("now", Timestamp.from(now)));
        return findInterest(saved.overlapId(), saved.vendorId()).orElseThrow();
    }

    public void expirePendingInterests(UUID overlapId, Instant now) {
        jdbc.update("""
                UPDATE overlap_interests
                SET status = 'expired', updated_at = :now
                WHERE overlap_id = :overlap_id AND status = 'pending'
                """, new MapSqlParameterSource("overlap_id", overlapId)
                .addValue("now", Timestamp.from(now)));
    }

    public void restoreSavedInterests(UUID overlapId, Instant now) {
        jdbc.update("""
                UPDATE overlap_interests i
                SET status = 'pending', updated_at = :now
                WHERE i.overlap_id = :overlap_id
                  AND i.status = 'expired'
                  AND EXISTS (
                    SELECT 1 FROM saved_plan_activations s
                    WHERE s.overlap_id = i.overlap_id
                      AND s.vendor_id = i.interested_vendor_id)
                """, new MapSqlParameterSource("overlap_id", overlapId)
                .addValue("now", Timestamp.from(now)));
    }

    public Optional<OverlapInterest> findInterest(UUID overlapId, UUID interestedVendorId) {
        return optional("""
                SELECT * FROM overlap_interests
                WHERE overlap_id = :overlap_id
                  AND interested_vendor_id = :interested_id
                """, new MapSqlParameterSource("overlap_id", overlapId)
                .addValue("interested_id", interestedVendorId), this::mapInterest);
    }

    public List<OverlapInterest> listInterests(
            UUID sellerVendorId, UUID overlapId, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM overlap_interests
                WHERE overlap_id = :overlap_id
                  AND counterparty_vendor_id = :seller_id
                ORDER BY score DESC, created_at, id
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("overlap_id", overlapId)
                .addValue("seller_id", sellerVendorId)
                .addValue("limit", limit)
                .addValue("offset", offset), this::mapInterest);
    }

    public NotificationRecord upsertNotification(
            UUID vendorId, UUID eventId, NotificationTrigger trigger,
            UUID overlapId, UUID cardId, UUID counterpartyVendorId,
            String payloadJson, Instant sourceOccurredAt, Instant availableAt, Instant now) {
        MapSqlParameterSource params = notificationParams(
                vendorId, eventId, trigger, overlapId, cardId, counterpartyVendorId,
                payloadJson, sourceOccurredAt, availableAt, now);
        try {
            return jdbc.queryForObject("""
                    INSERT INTO notifications (
                        vendor_id, event_id, trigger_type, overlap_id, card_id,
                        counterparty_vendor_id, payload, active, is_read,
                        source_occurred_at, available_at, created_at, updated_at)
                    VALUES (
                        :vendor_id, :event_id, :trigger, :overlap_id, :card_id,
                        :counterparty_id, CAST(:payload AS jsonb), TRUE, FALSE,
                        :source_at, :available_at, :now, :now)
                    ON CONFLICT (vendor_id, overlap_id, trigger_type) DO UPDATE SET
                        event_id = EXCLUDED.event_id,
                        card_id = EXCLUDED.card_id,
                        counterparty_vendor_id = EXCLUDED.counterparty_vendor_id,
                        payload = EXCLUDED.payload,
                        active = TRUE,
                        is_read = CASE
                            WHEN notifications.payload IS DISTINCT FROM EXCLUDED.payload
                              OR NOT notifications.active THEN FALSE
                            ELSE notifications.is_read
                        END,
                        source_occurred_at = EXCLUDED.source_occurred_at,
                        available_at = EXCLUDED.available_at,
                        updated_at = EXCLUDED.updated_at
                    WHERE EXCLUDED.source_occurred_at > notifications.source_occurred_at
                       OR (EXCLUDED.source_occurred_at = notifications.source_occurred_at
                           AND (notifications.payload IS DISTINCT FROM EXCLUDED.payload
                             OR notifications.active IS DISTINCT FROM TRUE
                             OR notifications.available_at IS DISTINCT FROM EXCLUDED.available_at))
                    RETURNING *
                    """, params, this::mapNotification);
        } catch (EmptyResultDataAccessException e) {
            return findByUnique(vendorId, overlapId, trigger).orElseThrow();
        }
    }

    public void deactivateOverlapNotifications(UUID overlapId, Instant sourceAt, Instant now) {
        jdbc.update("""
                UPDATE notifications
                SET active = FALSE,
                    source_occurred_at = GREATEST(source_occurred_at, :source_at),
                    updated_at = :now
                WHERE overlap_id = :overlap_id
                  AND active
                  AND source_occurred_at <= :source_at
                """, new MapSqlParameterSource("overlap_id", overlapId)
                .addValue("source_at", Timestamp.from(sourceAt))
                .addValue("now", Timestamp.from(now)));
    }

    public void deactivateTrigger(
            UUID vendorId, UUID overlapId, NotificationTrigger trigger,
            Instant sourceAt, Instant now) {
        jdbc.update("""
                UPDATE notifications
                SET active = FALSE,
                    source_occurred_at = GREATEST(source_occurred_at, :source_at),
                    updated_at = :now
                WHERE vendor_id = :vendor_id
                  AND overlap_id = :overlap_id
                  AND trigger_type = :trigger
                  AND active
                  AND source_occurred_at <= :source_at
                """, new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("overlap_id", overlapId)
                .addValue("trigger", trigger.databaseValue())
                .addValue("source_at", Timestamp.from(sourceAt))
                .addValue("now", Timestamp.from(now)));
    }

    public List<NotificationRecord> listVisible(
            UUID vendorId, UUID eventId, Instant now, int limit, int offset) {
        return jdbc.query("""
                SELECT n.*
                FROM notifications n
                JOIN notification_preferences p ON p.user_id = n.vendor_id
                WHERE n.vendor_id = :vendor_id
                  AND n.active
                  AND n.available_at IS NOT NULL
                  AND n.available_at <= :now
                  AND p.in_app_enabled
                  AND COALESCE((p.triggers ->> n.trigger_type)::boolean, TRUE)
                  AND NOT jsonb_exists(p.event_mutes, n.event_id::text)
                  AND (CAST(:event_id AS uuid) IS NULL OR n.event_id = CAST(:event_id AS uuid))
                ORDER BY n.available_at DESC, n.updated_at DESC, n.id
                LIMIT :limit OFFSET :offset
                """, pageParams(vendorId, eventId, now, limit, offset),
                this::mapNotification);
    }

    public long unreadCount(UUID vendorId, UUID eventId, Instant now) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM notifications n
                JOIN notification_preferences p ON p.user_id = n.vendor_id
                WHERE n.vendor_id = :vendor_id
                  AND n.active
                  AND NOT n.is_read
                  AND n.available_at IS NOT NULL
                  AND n.available_at <= :now
                  AND p.in_app_enabled
                  AND COALESCE((p.triggers ->> n.trigger_type)::boolean, TRUE)
                  AND NOT jsonb_exists(p.event_mutes, n.event_id::text)
                  AND (CAST(:event_id AS uuid) IS NULL OR n.event_id = CAST(:event_id AS uuid))
                """, pageParams(vendorId, eventId, now, 0, 0), Long.class);
        return value == null ? 0 : value;
    }

    public Optional<NotificationRecord> markRead(UUID vendorId, UUID id, Instant now) {
        return optional("""
                UPDATE notifications
                SET is_read = TRUE, updated_at = :now
                WHERE id = :id AND vendor_id = :vendor_id AND active
                RETURNING *
                """, new MapSqlParameterSource("id", id)
                .addValue("vendor_id", vendorId)
                .addValue("now", Timestamp.from(now)), this::mapNotification);
    }

    public List<NotificationRecord> activeForVendor(UUID vendorId) {
        return jdbc.query("""
                SELECT * FROM notifications
                WHERE vendor_id = :vendor_id AND active
                ORDER BY id
                """, new MapSqlParameterSource("vendor_id", vendorId),
                this::mapNotification);
    }

    public List<NotificationRecord> activeForEvent(UUID eventId) {
        return jdbc.query("""
                SELECT * FROM notifications
                WHERE event_id = :event_id AND active
                ORDER BY id
                """, new MapSqlParameterSource("event_id", eventId),
                this::mapNotification);
    }

    public void updateAvailability(UUID notificationId, Instant availableAt, Instant now) {
        jdbc.update("""
                UPDATE notifications
                SET available_at = :available_at, updated_at = :now
                WHERE id = :id
                  AND available_at IS DISTINCT FROM :available_at
                """, new MapSqlParameterSource("id", notificationId)
                .addValue("available_at", timestamp(availableAt))
                .addValue("now", Timestamp.from(now)));
    }

    public NotificationPreferences getOrCreatePreferences(UUID userId, Instant now) {
        jdbc.update("""
                INSERT INTO notification_preferences (user_id, updated_at)
                VALUES (:user_id, :now)
                ON CONFLICT (user_id) DO NOTHING
                """, new MapSqlParameterSource("user_id", userId)
                .addValue("now", Timestamp.from(now)));
        return findPreferences(userId).orElseThrow();
    }

    public Optional<NotificationPreferences> findPreferences(UUID userId) {
        return optional("""
                SELECT * FROM notification_preferences WHERE user_id = :user_id
                """, new MapSqlParameterSource("user_id", userId), this::mapPreferences);
    }

    public NotificationPreferences savePreferences(NotificationPreferences preferences) {
        String triggers = writeTriggers(preferences);
        String mutes = writeMutes(preferences.mutedEventIds());
        return jdbc.queryForObject("""
                INSERT INTO notification_preferences (
                    user_id, in_app_enabled, email_enabled, triggers,
                    digest_mode, event_mutes, updated_at)
                VALUES (
                    :user_id, :in_app, :email, CAST(:triggers AS jsonb),
                    :digest, CAST(:mutes AS jsonb), :updated_at)
                ON CONFLICT (user_id) DO UPDATE SET
                    in_app_enabled = EXCLUDED.in_app_enabled,
                    email_enabled = EXCLUDED.email_enabled,
                    triggers = EXCLUDED.triggers,
                    digest_mode = EXCLUDED.digest_mode,
                    event_mutes = EXCLUDED.event_mutes,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """, new MapSqlParameterSource("user_id", preferences.userId())
                .addValue("in_app", preferences.inAppEnabled())
                .addValue("email", preferences.emailEnabled())
                .addValue("triggers", triggers)
                .addValue("digest", preferences.digestMode().databaseValue())
                .addValue("mutes", mutes)
                .addValue("updated_at", Timestamp.from(preferences.updatedAt())),
                this::mapPreferences);
    }

    private Optional<NotificationRecord> findByUnique(
            UUID vendorId, UUID overlapId, NotificationTrigger trigger) {
        return optional("""
                SELECT * FROM notifications
                WHERE vendor_id = :vendor_id
                  AND overlap_id = :overlap_id
                  AND trigger_type = :trigger
                """, new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("overlap_id", overlapId)
                .addValue("trigger", trigger.databaseValue()), this::mapNotification);
    }

    private EventSchedule mapEvent(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new EventSchedule(
                (UUID) rs.getObject("event_id"), rs.getString("name"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private OverlapProjection mapOverlap(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new OverlapProjection(
                (UUID) rs.getObject("overlap_id"),
                (UUID) rs.getObject("event_id"),
                (UUID) rs.getObject("buyer_vendor_id"),
                (UUID) rs.getObject("seller_vendor_id"),
                (UUID) rs.getObject("card_id"),
                rs.getString("inventory_priority"), rs.getBigDecimal("score"),
                rs.getString("payload"), rs.getBoolean("active"),
                rs.getInt("action_rank"), rs.getTimestamp("occurred_at").toInstant());
    }

    private SavedPlan mapSavedPlan(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Timestamp activated = rs.getTimestamp("activated_at");
        return new SavedPlan(
                (UUID) rs.getObject("saved_overlap_id"),
                (UUID) rs.getObject("overlap_id"),
                (UUID) rs.getObject("vendor_id"),
                (UUID) rs.getObject("event_id"),
                (UUID) rs.getObject("buyer_vendor_id"),
                (UUID) rs.getObject("seller_vendor_id"),
                (UUID) rs.getObject("card_id"), rs.getBigDecimal("score"),
                rs.getTimestamp("saved_at").toInstant(),
                activated == null ? null : activated.toInstant());
    }

    private NotificationRecord mapNotification(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Timestamp available = rs.getTimestamp("available_at");
        return new NotificationRecord(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("vendor_id"),
                (UUID) rs.getObject("event_id"),
                NotificationTrigger.parse(rs.getString("trigger_type")),
                (UUID) rs.getObject("overlap_id"), (UUID) rs.getObject("card_id"),
                (UUID) rs.getObject("counterparty_vendor_id"), rs.getString("payload"),
                rs.getBoolean("is_read"), rs.getBoolean("active"),
                rs.getTimestamp("source_occurred_at").toInstant(),
                available == null ? null : available.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private OverlapInterest mapInterest(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new OverlapInterest(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("overlap_id"),
                (UUID) rs.getObject("interested_vendor_id"),
                (UUID) rs.getObject("counterparty_vendor_id"),
                (UUID) rs.getObject("event_id"), rs.getBigDecimal("score"),
                InterestStatus.parse(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private NotificationPreferences mapPreferences(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        try {
            JsonNode triggers = objectMapper.readTree(rs.getString("triggers"));
            JsonNode mutes = objectMapper.readTree(rs.getString("event_mutes"));
            Set<UUID> mutedIds = new HashSet<>();
            mutes.forEach(value -> mutedIds.add(UUID.fromString(value.asText())));
            return new NotificationPreferences(
                    (UUID) rs.getObject("user_id"), rs.getBoolean("in_app_enabled"),
                    rs.getBoolean("email_enabled"),
                    triggerValue(triggers, NotificationTrigger.OVERLAP_BUYLIST),
                    triggerValue(triggers, NotificationTrigger.OVERLAP_LIQUIDATE),
                    triggerValue(triggers, NotificationTrigger.SAVED_OVERLAP_ACTIVE),
                    DigestMode.parse(rs.getString("digest_mode")), Set.copyOf(mutedIds),
                    rs.getTimestamp("updated_at").toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("invalid notification preference JSON", e);
        }
    }

    private static boolean triggerValue(JsonNode triggers, NotificationTrigger trigger) {
        JsonNode value = triggers.get(trigger.databaseValue());
        return value == null || value.asBoolean(true);
    }

    private String writeTriggers(NotificationPreferences preferences) {
        var values = objectMapper.createObjectNode();
        values.put(NotificationTrigger.OVERLAP_BUYLIST.databaseValue(),
                preferences.overlapBuyListEnabled());
        values.put(NotificationTrigger.OVERLAP_LIQUIDATE.databaseValue(),
                preferences.overlapLiquidateEnabled());
        values.put(NotificationTrigger.SAVED_OVERLAP_ACTIVE.databaseValue(),
                preferences.savedOverlapActiveEnabled());
        return values.toString();
    }

    private String writeMutes(Set<UUID> eventIds) {
        var values = objectMapper.createArrayNode();
        eventIds.stream().map(UUID::toString).sorted().forEach(values::add);
        return values.toString();
    }

    private static MapSqlParameterSource overlapParams(OverlapProjection value) {
        return new MapSqlParameterSource("overlap_id", value.overlapId())
                .addValue("event_id", value.eventId())
                .addValue("buyer_id", value.buyerVendorId())
                .addValue("seller_id", value.sellerVendorId())
                .addValue("card_id", value.cardId())
                .addValue("priority", value.inventoryPriority())
                .addValue("score", value.score())
                .addValue("payload", value.payloadJson())
                .addValue("active", value.active())
                .addValue("action_rank", value.actionRank())
                .addValue("occurred_at", Timestamp.from(value.occurredAt()));
    }

    private static MapSqlParameterSource savedParams(SavedPlan value) {
        return new MapSqlParameterSource("saved_id", value.savedOverlapId())
                .addValue("overlap_id", value.overlapId())
                .addValue("vendor_id", value.vendorId())
                .addValue("event_id", value.eventId())
                .addValue("buyer_id", value.buyerVendorId())
                .addValue("seller_id", value.sellerVendorId())
                .addValue("card_id", value.cardId())
                .addValue("score", value.score())
                .addValue("saved_at", Timestamp.from(value.savedAt()));
    }

    private static MapSqlParameterSource notificationParams(
            UUID vendorId, UUID eventId, NotificationTrigger trigger,
            UUID overlapId, UUID cardId, UUID counterpartyVendorId,
            String payload, Instant sourceAt, Instant availableAt, Instant now) {
        return new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("event_id", eventId)
                .addValue("trigger", trigger.databaseValue())
                .addValue("overlap_id", overlapId)
                .addValue("card_id", cardId)
                .addValue("counterparty_id", counterpartyVendorId)
                .addValue("payload", payload)
                .addValue("source_at", Timestamp.from(sourceAt))
                .addValue("available_at", timestamp(availableAt))
                .addValue("now", Timestamp.from(now));
    }

    private static MapSqlParameterSource pageParams(
            UUID vendorId, UUID eventId, Instant now, int limit, int offset) {
        return new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("event_id", eventId == null ? null : eventId.toString())
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit)
                .addValue("offset", offset);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private <T> Optional<T> optional(
            String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
