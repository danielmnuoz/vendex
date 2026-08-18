package com.vendex.notification.service;

import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.OverlapInterest;
import com.vendex.notification.domain.OverlapProjection;
import com.vendex.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationQueryService {
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;
    private final NotificationProjectionService projections;
    private final Clock clock;

    public NotificationQueryService(
            NotificationRepository repository,
            NotificationProjectionService projections,
            Clock clock) {
        this.repository = repository;
        this.projections = projections;
        this.clock = clock;
    }

    @Transactional
    public NotificationPage getNotifications(
            UUID vendorId, UUID eventId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        int pageSize = pageSize(requestedPageSize, offset);
        repository.getOrCreatePreferences(vendorId, clock.instant());
        return notificationPage(repository.listVisible(
                vendorId, eventId, clock.instant(), pageSize + 1, offset),
                pageSize, offset);
    }

    @Transactional
    public NotificationRecord markRead(UUID vendorId, UUID notificationId) {
        requireId(vendorId, "vendor_id");
        requireId(notificationId, "notification_id");
        return repository.markRead(vendorId, notificationId, clock.instant())
                .orElseThrow(NotificationExceptions.NotFoundException::new);
    }

    @Transactional
    public long unreadCount(UUID vendorId, UUID eventId) {
        requireId(vendorId, "vendor_id");
        repository.getOrCreatePreferences(vendorId, clock.instant());
        return repository.unreadCount(vendorId, eventId, clock.instant());
    }

    @Transactional
    public NotificationPreferences getPreferences(UUID userId) {
        requireId(userId, "user_id");
        return repository.getOrCreatePreferences(userId, clock.instant());
    }

    @Transactional
    public NotificationPreferences updatePreferences(UUID userId, PreferencesPatch patch) {
        requireId(userId, "user_id");
        if (patch == null) {
            throw new NotificationExceptions.ValidationException("preferences patch is required");
        }
        NotificationPreferences current = repository.getOrCreatePreferences(
                userId, clock.instant());
        Set<UUID> muted = patch.replaceMutedEventIds()
                ? Set.copyOf(patch.mutedEventIds() == null ? Set.of() : patch.mutedEventIds())
                : current.mutedEventIds();
        NotificationPreferences updated = repository.savePreferences(
                new NotificationPreferences(
                        userId,
                        value(patch.inAppEnabled(), current.inAppEnabled()),
                        value(patch.emailEnabled(), current.emailEnabled()),
                        value(patch.overlapBuyListEnabled(), current.overlapBuyListEnabled()),
                        value(patch.overlapLiquidateEnabled(),
                                current.overlapLiquidateEnabled()),
                        value(patch.savedOverlapActiveEnabled(),
                                current.savedOverlapActiveEnabled()),
                        patch.digestMode() == null ? current.digestMode() : patch.digestMode(),
                        muted, clock.instant()));
        projections.rescheduleVendor(userId);
        return updated;
    }

    public InterestPage listInterests(
            UUID sellerVendorId, UUID overlapId, int requestedPageSize, int offset) {
        requireId(sellerVendorId, "seller_vendor_id");
        requireId(overlapId, "overlap_id");
        OverlapProjection overlap = repository.findOverlap(overlapId)
                .orElseThrow(NotificationExceptions.NotFoundException::new);
        if (!overlap.sellerVendorId().equals(sellerVendorId)) {
            throw new NotificationExceptions.OwnershipException();
        }
        int pageSize = pageSize(requestedPageSize, offset);
        List<OverlapInterest> rows = repository.listInterests(
                sellerVendorId, overlapId, pageSize + 1, offset);
        boolean hasMore = rows.size() > pageSize;
        List<OverlapInterest> page = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize)) : new ArrayList<>(rows);
        return new InterestPage(List.copyOf(page),
                hasMore ? offset + pageSize : 0, hasMore);
    }

    private static NotificationPage notificationPage(
            List<NotificationRecord> rows, int pageSize, int offset) {
        boolean hasMore = rows.size() > pageSize;
        List<NotificationRecord> page = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize)) : new ArrayList<>(rows);
        return new NotificationPage(List.copyOf(page),
                hasMore ? offset + pageSize : 0, hasMore);
    }

    private static int pageSize(int requested, int offset) {
        if (offset < 0) {
            throw new NotificationExceptions.ValidationException(
                    "page_offset must not be negative");
        }
        return requested <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, MAX_PAGE_SIZE);
    }

    private static boolean value(Boolean proposed, boolean current) {
        return proposed == null ? current : proposed;
    }

    private static void requireId(UUID value, String field) {
        if (value == null) {
            throw new NotificationExceptions.ValidationException(field + " is required");
        }
    }

    public record PreferencesPatch(
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean overlapBuyListEnabled,
            Boolean overlapLiquidateEnabled,
            Boolean savedOverlapActiveEnabled,
            DigestMode digestMode,
            Set<UUID> mutedEventIds,
            boolean replaceMutedEventIds
    ) {}

    public record NotificationPage(
            List<NotificationRecord> notifications, int nextPageOffset, boolean hasMore) {}

    public record InterestPage(
            List<OverlapInterest> interests, int nextPageOffset, boolean hasMore) {}
}
