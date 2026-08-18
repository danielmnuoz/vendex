package com.vendex.gateway.api;

import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.workflow.GrpcRequestSupport;
import com.vendex.gateway.workflow.VendorDirectory;
import com.vendex.notification.v1.DigestMode;
import com.vendex.notification.v1.GetNotificationPreferencesRequest;
import com.vendex.notification.v1.GetNotificationsRequest;
import com.vendex.notification.v1.GetUnreadCountRequest;
import com.vendex.notification.v1.MarkAsReadRequest;
import com.vendex.notification.v1.Notification;
import com.vendex.notification.v1.NotificationPreferences;
import com.vendex.notification.v1.NotificationServiceGrpc;
import com.vendex.notification.v1.NotificationTrigger;
import com.vendex.notification.v1.UpdateNotificationPreferencesRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationServiceGrpc.NotificationServiceBlockingStub notifications;
    private final GatewayProperties properties;
    private final VendorDirectory vendors;

    public NotificationController(
            NotificationServiceGrpc.NotificationServiceBlockingStub notifications,
            GatewayProperties properties,
            VendorDirectory vendors) {
        this.notifications = notifications;
        this.properties = properties;
        this.vendors = vendors;
    }

    @GetMapping
    PageResponse<NotificationResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String eventId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().getNotifications(GetNotificationsRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setEventId(eventId)
                .setPageSize(pageSize)
                .setPageOffset(pageOffset)
                .build());
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request,
                response.getNotificationsList().stream()
                        .map(Notification::getCounterpartyVendorId).toList(),
                null);
        return new PageResponse<>(response.getNotificationsList().stream()
                .map(notification -> map(notification,
                        directory.get(notification.getCounterpartyVendorId())))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @PatchMapping("/{notificationId}/read")
    NotificationResponse markRead(
            HttpServletRequest request,
            @PathVariable String notificationId) {
        GatewayPrincipal principal = vendor(request);
        Notification notification = stub().markAsRead(MarkAsReadRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setNotificationId(notificationId)
                .build()).getNotification();
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request, List.of(notification.getCounterpartyVendorId()), null);
        return map(notification, directory.get(notification.getCounterpartyVendorId()));
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String eventId) {
        GatewayPrincipal principal = vendor(request);
        long count = stub().getUnreadCount(GetUnreadCountRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setEventId(eventId)
                .build()).getUnreadCount();
        return new UnreadCountResponse(count);
    }

    @GetMapping("/preferences")
    PreferencesResponse preferences(HttpServletRequest request) {
        GatewayPrincipal principal = vendor(request);
        return map(stub().getNotificationPreferences(GetNotificationPreferencesRequest.newBuilder()
                .setUserId(principal.userId().toString())
                .build()).getPreferences());
    }

    @PatchMapping("/preferences")
    PreferencesResponse updatePreferences(
            HttpServletRequest request,
            @Valid @RequestBody PreferencesPatchBody body) {
        GatewayPrincipal principal = vendor(request);
        UpdateNotificationPreferencesRequest.Builder update =
                UpdateNotificationPreferencesRequest.newBuilder()
                        .setUserId(principal.userId().toString());
        if (body.inAppEnabled() != null) {
            update.setInAppEnabled(body.inAppEnabled());
        }
        if (body.emailEnabled() != null) {
            update.setEmailEnabled(body.emailEnabled());
        }
        if (body.overlapBuyListEnabled() != null) {
            update.setOverlapBuylistEnabled(body.overlapBuyListEnabled());
        }
        if (body.overlapLiquidateEnabled() != null) {
            update.setOverlapLiquidateEnabled(body.overlapLiquidateEnabled());
        }
        if (body.savedOverlapActiveEnabled() != null) {
            update.setSavedOverlapActiveEnabled(body.savedOverlapActiveEnabled());
        }
        if (body.digestMode() != null) {
            update.setDigestMode(digestMode(body.digestMode()));
        }
        if (body.mutedEventIds() != null) {
            update.addAllMutedEventIds(body.mutedEventIds());
            update.setReplaceMutedEventIds(true);
        }
        return map(stub().updateNotificationPreferences(update.build()).getPreferences());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private NotificationServiceGrpc.NotificationServiceBlockingStub stub() {
        return GrpcRequestSupport.deadline(notifications, properties);
    }

    private static NotificationResponse map(
            Notification notification,
            VendorDirectory.PublicVendor counterparty) {
        return new NotificationResponse(
                notification.getId(), notification.getEventId(), trigger(notification.getTrigger()),
                notification.getOverlapId(), notification.getCardId(),
                notification.getCounterpartyVendorId(), notification.getPayloadJson(),
                notification.getRead(), notification.getActive(),
                notification.getAvailableAtEpochSeconds(), notification.getCreatedAtEpochSeconds(),
                notification.getUpdatedAtEpochSeconds(), counterparty);
    }

    private static PreferencesResponse map(NotificationPreferences preferences) {
        return new PreferencesResponse(
                preferences.getInAppEnabled(), preferences.getEmailEnabled(),
                preferences.getOverlapBuylistEnabled(), preferences.getOverlapLiquidateEnabled(),
                preferences.getSavedOverlapActiveEnabled(), digestMode(preferences.getDigestMode()),
                preferences.getMutedEventIdsList(), preferences.getUpdatedAtEpochSeconds());
    }

    private static String trigger(NotificationTrigger trigger) {
        return switch (trigger) {
            case NOTIFICATION_TRIGGER_OVERLAP_BUYLIST -> "overlap_buylist";
            case NOTIFICATION_TRIGGER_OVERLAP_LIQUIDATE -> "overlap_liquidate";
            case NOTIFICATION_TRIGGER_SAVED_OVERLAP_ACTIVE -> "saved_overlap_active";
            default -> "unspecified";
        };
    }

    private static DigestMode digestMode(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "REAL_TIME" -> DigestMode.DIGEST_MODE_REAL_TIME;
            case "DAILY" -> DigestMode.DIGEST_MODE_DAILY;
            case "EVENT_ONLY" -> DigestMode.DIGEST_MODE_EVENT_ONLY;
            default -> throw new IllegalArgumentException(
                    "digestMode must be real_time, daily, or event_only");
        };
    }

    private static String digestMode(DigestMode mode) {
        return switch (mode) {
            case DIGEST_MODE_REAL_TIME -> "real_time";
            case DIGEST_MODE_DAILY -> "daily";
            case DIGEST_MODE_EVENT_ONLY -> "event_only";
            default -> "unspecified";
        };
    }

    public record NotificationResponse(
            String id,
            String eventId,
            String trigger,
            String overlapId,
            String cardId,
            String counterpartyVendorId,
            String payloadJson,
            boolean read,
            boolean active,
            long availableAtEpochSeconds,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds,
            VendorDirectory.PublicVendor counterparty) {}

    public record UnreadCountResponse(long unreadCount) {}

    public record PreferencesPatchBody(
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean overlapBuyListEnabled,
            Boolean overlapLiquidateEnabled,
            Boolean savedOverlapActiveEnabled,
            String digestMode,
            @Size(max = 100) List<String> mutedEventIds) {}

    public record PreferencesResponse(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean overlapBuyListEnabled,
            boolean overlapLiquidateEnabled,
            boolean savedOverlapActiveEnabled,
            String digestMode,
            List<String> mutedEventIds,
            long updatedAtEpochSeconds) {}
}
