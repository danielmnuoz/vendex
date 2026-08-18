package com.vendex.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.EventCreated;
import com.vendex.events.contract.EventUpdated;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.OverlapSaved;
import com.vendex.notification.domain.EventSchedule;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.NotificationTrigger;
import com.vendex.notification.domain.OverlapProjection;
import com.vendex.notification.domain.SavedPlan;
import com.vendex.notification.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationProjectionService {

    private final NotificationRepository repository;
    private final NotificationDeliveryPolicy deliveryPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationProjectionService(
            NotificationRepository repository,
            NotificationDeliveryPolicy deliveryPolicy,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.deliveryPolicy = deliveryPolicy;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void applyEvent(EventCreated fact) {
        Objects.requireNonNull(fact, "event fact is required");
        applyEvent(new EventSchedule(fact.eventId(), fact.name(), fact.startDate(),
                fact.endDate(), fact.timestamp()));
    }

    @Transactional
    public void applyEvent(EventUpdated fact) {
        Objects.requireNonNull(fact, "event fact is required");
        applyEvent(new EventSchedule(fact.eventId(), fact.name(), fact.startDate(),
                fact.endDate(), fact.timestamp()));
    }

    @Transactional
    public void applyOverlap(OverlapFound fact, String payloadJson) {
        validateOverlap(fact);
        OverlapProjection stored = repository.upsertOverlap(new OverlapProjection(
                fact.overlapId(), fact.eventId(), fact.buyerVendorId(),
                fact.sellerVendorId(), fact.cardId(), fact.inventoryPriority().toLowerCase(),
                fact.score(), payloadJson, fact.action() != Action.REMOVED,
                actionRank(fact.action()), fact.timestamp()));
        reconcileOverlap(stored);
    }

    @Transactional
    public void applySaved(OverlapSaved fact) {
        validateSaved(fact);
        SavedPlan saved = repository.upsertSavedPlan(new SavedPlan(
                fact.savedOverlapId(), fact.overlapId(), fact.vendorId(), fact.eventId(),
                fact.buyerVendorId(), fact.sellerVendorId(), fact.cardId(), fact.score(),
                fact.timestamp(), null));
        repository.upsertInterest(saved, saved.savedAt());
        repository.findOverlap(saved.overlapId())
                .filter(OverlapProjection::active)
                .ifPresent(overlap -> activateSavedPlanIfDue(saved, overlap));
    }

    @Transactional
    @Scheduled(
            fixedDelayString = "${notification.activation-poll-ms:1000}",
            initialDelayString = "${notification.activation-initial-delay-ms:1000}")
    public void activateDueSavedPlans() {
        Instant now = clock.instant();
        for (SavedPlan saved : repository.dueSavedPlans(deliveryPolicy.today(now))) {
            repository.findOverlap(saved.overlapId())
                    .filter(OverlapProjection::active)
                    .ifPresent(overlap -> activateSavedPlan(saved, overlap));
        }
    }

    @Transactional
    public void rescheduleVendor(UUID vendorId) {
        NotificationPreferences preferences = repository.getOrCreatePreferences(
                vendorId, clock.instant());
        for (NotificationRecord notification : repository.activeForVendor(vendorId)) {
            EventSchedule event = repository.findEvent(notification.eventId()).orElse(null);
            repository.updateAvailability(notification.id(),
                    deliveryPolicy.availableAt(notification, preferences, event), clock.instant());
        }
    }

    private void applyEvent(EventSchedule event) {
        validateEvent(event);
        EventSchedule stored = repository.upsertEvent(event);
        Instant now = clock.instant();
        for (NotificationRecord notification : repository.activeForEvent(stored.eventId())) {
            NotificationPreferences preferences = repository.getOrCreatePreferences(
                    notification.vendorId(), now);
            repository.updateAvailability(notification.id(),
                    deliveryPolicy.availableAt(notification, preferences, stored), now);
        }
        activateDueSavedPlans();
    }

    private void reconcileOverlap(OverlapProjection overlap) {
        Instant now = clock.instant();
        if (!overlap.active()) {
            repository.deactivateOverlapNotifications(
                    overlap.overlapId(), overlap.occurredAt(), now);
            repository.expirePendingInterests(overlap.overlapId(), now);
            return;
        }

        repository.restoreSavedInterests(overlap.overlapId(), now);
        upsertLiveNotification(overlap, overlap.buyerVendorId(), overlap.sellerVendorId(),
                NotificationTrigger.OVERLAP_BUYLIST);

        if ("liquidate".equals(overlap.inventoryPriority())) {
            upsertLiveNotification(overlap, overlap.sellerVendorId(), overlap.buyerVendorId(),
                    NotificationTrigger.OVERLAP_LIQUIDATE);
        } else {
            repository.deactivateTrigger(
                    overlap.sellerVendorId(), overlap.overlapId(),
                    NotificationTrigger.OVERLAP_LIQUIDATE, overlap.occurredAt(), now);
        }

        for (SavedPlan saved : repository.savedPlansForOverlap(overlap.overlapId())) {
            activateSavedPlanIfDue(saved, overlap);
        }
    }

    private void upsertLiveNotification(
            OverlapProjection overlap, UUID vendorId, UUID counterparty,
            NotificationTrigger trigger) {
        Instant now = clock.instant();
        NotificationPreferences preferences = repository.getOrCreatePreferences(vendorId, now);
        EventSchedule event = repository.findEvent(overlap.eventId()).orElse(null);
        repository.upsertNotification(
                vendorId, overlap.eventId(), trigger, overlap.overlapId(), overlap.cardId(),
                counterparty, overlap.payloadJson(), overlap.occurredAt(),
                deliveryPolicy.availableAt(trigger, overlap.occurredAt(), preferences, event),
                now);
    }

    private void activateSavedPlanIfDue(SavedPlan saved, OverlapProjection overlap) {
        EventSchedule event = repository.findEvent(saved.eventId()).orElse(null);
        Instant now = clock.instant();
        if (event != null && !event.startDate().isAfter(deliveryPolicy.today(now))) {
            activateSavedPlan(saved, overlap);
        }
    }

    private void activateSavedPlan(SavedPlan saved, OverlapProjection overlap) {
        EventSchedule event = repository.findEvent(saved.eventId()).orElse(null);
        if (event == null || !overlap.active()) {
            return;
        }
        Instant eventStart = deliveryPolicy.eventStart(event.startDate());
        Instant sourceAt = max(saved.savedAt(), overlap.occurredAt(), eventStart);
        Instant now = clock.instant();
        NotificationPreferences preferences = repository.getOrCreatePreferences(
                saved.vendorId(), now);
        repository.upsertNotification(
                saved.vendorId(), saved.eventId(), NotificationTrigger.SAVED_OVERLAP_ACTIVE,
                saved.overlapId(), saved.cardId(), saved.counterpartyVendorId(),
                savedPayload(saved, overlap), sourceAt,
                deliveryPolicy.availableAt(NotificationTrigger.SAVED_OVERLAP_ACTIVE,
                        sourceAt, preferences, event), now);
        repository.markSavedPlanActivated(saved.savedOverlapId(), now);
    }

    private String savedPayload(SavedPlan saved, OverlapProjection overlap) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("saved_overlap_id", saved.savedOverlapId().toString());
            root.put("overlap_id", saved.overlapId().toString());
            root.put("saved_at", saved.savedAt().toString());
            root.set("overlap", objectMapper.readTree(overlap.payloadJson()));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid saved-overlap payload", e);
        }
    }

    private static Instant max(Instant first, Instant second, Instant third) {
        Instant value = first.isAfter(second) ? first : second;
        return value.isAfter(third) ? value : third;
    }

    private static int actionRank(Action action) {
        return switch (action) {
            case ADDED -> 1;
            case UPDATED -> 2;
            case REMOVED -> 3;
        };
    }

    private static void validateEvent(EventSchedule event) {
        Objects.requireNonNull(event.eventId(), "event_id is required");
        if (event.name() == null || event.name().isBlank()) {
            throw new IllegalArgumentException("event name is required");
        }
        Objects.requireNonNull(event.startDate(), "start_date is required");
        Objects.requireNonNull(event.endDate(), "end_date is required");
        Objects.requireNonNull(event.occurredAt(), "timestamp is required");
        if (event.endDate().isBefore(event.startDate())) {
            throw new IllegalArgumentException("end_date must not precede start_date");
        }
    }

    private static void validateOverlap(OverlapFound fact) {
        Objects.requireNonNull(fact, "overlap fact is required");
        Objects.requireNonNull(fact.overlapId(), "overlap_id is required");
        Objects.requireNonNull(fact.eventId(), "event_id is required");
        Objects.requireNonNull(fact.buyerVendorId(), "buyer_vendor_id is required");
        Objects.requireNonNull(fact.sellerVendorId(), "seller_vendor_id is required");
        Objects.requireNonNull(fact.cardId(), "card_id is required");
        if (fact.buyerVendorId().equals(fact.sellerVendorId())) {
            throw new IllegalArgumentException("overlap vendors must differ");
        }
        Objects.requireNonNull(fact.inventoryPriority(), "inventory_priority is required");
        if (!List.of("normal", "liquidate").contains(
                fact.inventoryPriority().toLowerCase())) {
            throw new IllegalArgumentException("invalid inventory priority");
        }
        Objects.requireNonNull(fact.score(), "score is required");
        validateScore(fact.score());
        Objects.requireNonNull(fact.action(), "action is required");
        Objects.requireNonNull(fact.timestamp(), "timestamp is required");
    }

    private static void validateSaved(OverlapSaved fact) {
        Objects.requireNonNull(fact, "saved-overlap fact is required");
        Objects.requireNonNull(fact.savedOverlapId(), "saved_overlap_id is required");
        Objects.requireNonNull(fact.overlapId(), "overlap_id is required");
        Objects.requireNonNull(fact.vendorId(), "vendor_id is required");
        Objects.requireNonNull(fact.eventId(), "event_id is required");
        Objects.requireNonNull(fact.buyerVendorId(), "buyer_vendor_id is required");
        Objects.requireNonNull(fact.sellerVendorId(), "seller_vendor_id is required");
        Objects.requireNonNull(fact.cardId(), "card_id is required");
        Objects.requireNonNull(fact.score(), "score is required");
        validateScore(fact.score());
        Objects.requireNonNull(fact.timestamp(), "timestamp is required");
        if (!fact.vendorId().equals(fact.buyerVendorId())
                && !fact.vendorId().equals(fact.sellerVendorId())) {
            throw new IllegalArgumentException("saved vendor must participate in overlap");
        }
    }

    private static void validateScore(BigDecimal score) {
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
    }
}
