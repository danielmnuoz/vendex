package com.vendex.notification.grpc;

import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.OverlapInterest;
import com.vendex.notification.service.NotificationExceptions;
import com.vendex.notification.service.NotificationQueryService;
import com.vendex.notification.v1.GetNotificationPreferencesRequest;
import com.vendex.notification.v1.GetNotificationsRequest;
import com.vendex.notification.v1.GetNotificationsResponse;
import com.vendex.notification.v1.GetUnreadCountRequest;
import com.vendex.notification.v1.GetUnreadCountResponse;
import com.vendex.notification.v1.ListInterestsForOverlapRequest;
import com.vendex.notification.v1.ListInterestsForOverlapResponse;
import com.vendex.notification.v1.MarkAsReadRequest;
import com.vendex.notification.v1.MarkAsReadResponse;
import com.vendex.notification.v1.NotificationPreferencesResponse;
import com.vendex.notification.v1.NotificationServiceGrpc;
import com.vendex.notification.v1.UpdateNotificationPreferencesRequest;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@GrpcService
public class NotificationGrpcService
        extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationQueryService notifications;

    public NotificationGrpcService(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void getNotifications(
            GetNotificationsRequest request,
            StreamObserver<GetNotificationsResponse> observer) {
        try {
            var page = notifications.getNotifications(
                    uuid(request.getVendorId(), "vendor_id"),
                    optionalUuid(request.getEventId(), "event_id"),
                    request.getPageSize(), request.getPageOffset());
            var response = GetNotificationsResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.notifications().forEach(value -> response.addNotifications(toProto(value)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void markAsRead(
            MarkAsReadRequest request, StreamObserver<MarkAsReadResponse> observer) {
        try {
            NotificationRecord notification = notifications.markRead(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getNotificationId(), "notification_id"));
            observer.onNext(MarkAsReadResponse.newBuilder()
                    .setNotification(toProto(notification)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getUnreadCount(
            GetUnreadCountRequest request,
            StreamObserver<GetUnreadCountResponse> observer) {
        try {
            long count = notifications.unreadCount(
                    uuid(request.getVendorId(), "vendor_id"),
                    optionalUuid(request.getEventId(), "event_id"));
            observer.onNext(GetUnreadCountResponse.newBuilder()
                    .setUnreadCount(count).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getNotificationPreferences(
            GetNotificationPreferencesRequest request,
            StreamObserver<NotificationPreferencesResponse> observer) {
        try {
            NotificationPreferences preferences = notifications.getPreferences(
                    uuid(request.getUserId(), "user_id"));
            observer.onNext(NotificationPreferencesResponse.newBuilder()
                    .setPreferences(toProto(preferences)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void updateNotificationPreferences(
            UpdateNotificationPreferencesRequest request,
            StreamObserver<NotificationPreferencesResponse> observer) {
        try {
            Set<UUID> muted = new HashSet<>();
            for (String value : request.getMutedEventIdsList()) {
                muted.add(uuid(value, "muted_event_ids"));
            }
            DigestMode digest = request.hasDigestMode()
                    ? digest(request.getDigestMode()) : null;
            var patch = new NotificationQueryService.PreferencesPatch(
                    request.hasInAppEnabled() ? request.getInAppEnabled() : null,
                    request.hasEmailEnabled() ? request.getEmailEnabled() : null,
                    request.hasOverlapBuylistEnabled()
                            ? request.getOverlapBuylistEnabled() : null,
                    request.hasOverlapLiquidateEnabled()
                            ? request.getOverlapLiquidateEnabled() : null,
                    request.hasSavedOverlapActiveEnabled()
                            ? request.getSavedOverlapActiveEnabled() : null,
                    digest, Set.copyOf(muted), request.getReplaceMutedEventIds());
            NotificationPreferences preferences = notifications.updatePreferences(
                    uuid(request.getUserId(), "user_id"), patch);
            observer.onNext(NotificationPreferencesResponse.newBuilder()
                    .setPreferences(toProto(preferences)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listInterestsForOverlap(
            ListInterestsForOverlapRequest request,
            StreamObserver<ListInterestsForOverlapResponse> observer) {
        try {
            var page = notifications.listInterests(
                    uuid(request.getSellerVendorId(), "seller_vendor_id"),
                    uuid(request.getOverlapId(), "overlap_id"),
                    request.getPageSize(), request.getPageOffset());
            var response = ListInterestsForOverlapResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.interests().forEach(value -> response.addInterests(toProto(value)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    private static com.vendex.notification.v1.Notification toProto(
            NotificationRecord value) {
        return com.vendex.notification.v1.Notification.newBuilder()
                .setId(value.id().toString())
                .setVendorId(value.vendorId().toString())
                .setEventId(value.eventId().toString())
                .setTrigger(trigger(value.trigger()))
                .setOverlapId(value.overlapId().toString())
                .setCardId(value.cardId().toString())
                .setCounterpartyVendorId(value.counterpartyVendorId().toString())
                .setPayloadJson(value.payloadJson())
                .setRead(value.read())
                .setActive(value.active())
                .setAvailableAtEpochSeconds(value.availableAt() == null
                        ? 0 : value.availableAt().getEpochSecond())
                .setCreatedAtEpochSeconds(value.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(value.updatedAt().getEpochSecond())
                .build();
    }

    private static com.vendex.notification.v1.NotificationPreferences toProto(
            NotificationPreferences value) {
        var builder = com.vendex.notification.v1.NotificationPreferences.newBuilder()
                .setUserId(value.userId().toString())
                .setInAppEnabled(value.inAppEnabled())
                .setEmailEnabled(value.emailEnabled())
                .setOverlapBuylistEnabled(value.overlapBuyListEnabled())
                .setOverlapLiquidateEnabled(value.overlapLiquidateEnabled())
                .setSavedOverlapActiveEnabled(value.savedOverlapActiveEnabled())
                .setDigestMode(digest(value.digestMode()))
                .setUpdatedAtEpochSeconds(value.updatedAt().getEpochSecond());
        value.mutedEventIds().stream().map(UUID::toString).sorted()
                .forEach(builder::addMutedEventIds);
        return builder.build();
    }

    private static com.vendex.notification.v1.OverlapInterest toProto(
            OverlapInterest value) {
        return com.vendex.notification.v1.OverlapInterest.newBuilder()
                .setId(value.id().toString())
                .setOverlapId(value.overlapId().toString())
                .setInterestedVendorId(value.interestedVendorId().toString())
                .setCounterpartyVendorId(value.counterpartyVendorId().toString())
                .setEventId(value.eventId().toString())
                .setScore(value.score().stripTrailingZeros().toPlainString())
                .setStatus(switch (value.status()) {
                    case PENDING -> com.vendex.notification.v1.InterestStatus.INTEREST_STATUS_PENDING;
                    case REVEALED -> com.vendex.notification.v1.InterestStatus.INTEREST_STATUS_REVEALED;
                    case DECLINED -> com.vendex.notification.v1.InterestStatus.INTEREST_STATUS_DECLINED;
                    case EXPIRED -> com.vendex.notification.v1.InterestStatus.INTEREST_STATUS_EXPIRED;
                })
                .setCreatedAtEpochSeconds(value.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(value.updatedAt().getEpochSecond())
                .build();
    }

    private static com.vendex.notification.v1.NotificationTrigger trigger(
            com.vendex.notification.domain.NotificationTrigger value) {
        return switch (value) {
            case OVERLAP_BUYLIST -> com.vendex.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_BUYLIST;
            case OVERLAP_LIQUIDATE -> com.vendex.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_LIQUIDATE;
            case SAVED_OVERLAP_ACTIVE -> com.vendex.notification.v1.NotificationTrigger.NOTIFICATION_TRIGGER_SAVED_OVERLAP_ACTIVE;
        };
    }

    private static com.vendex.notification.v1.DigestMode digest(DigestMode value) {
        return switch (value) {
            case REAL_TIME -> com.vendex.notification.v1.DigestMode.DIGEST_MODE_REAL_TIME;
            case DAILY -> com.vendex.notification.v1.DigestMode.DIGEST_MODE_DAILY;
            case EVENT_ONLY -> com.vendex.notification.v1.DigestMode.DIGEST_MODE_EVENT_ONLY;
        };
    }

    private static DigestMode digest(com.vendex.notification.v1.DigestMode value) {
        return switch (value) {
            case DIGEST_MODE_REAL_TIME -> DigestMode.REAL_TIME;
            case DIGEST_MODE_DAILY -> DigestMode.DAILY;
            case DIGEST_MODE_EVENT_ONLY -> DigestMode.EVENT_ONLY;
            case DIGEST_MODE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new NotificationExceptions.ValidationException(
                            "digest_mode must be specified");
        };
    }

    private static UUID optionalUuid(String value, String field) {
        return value == null || value.isBlank() ? null : uuid(value, field);
    }

    private static UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NotificationExceptions.ValidationException(field + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new NotificationExceptions.ValidationException(field + " must be a UUID");
        }
    }
}
