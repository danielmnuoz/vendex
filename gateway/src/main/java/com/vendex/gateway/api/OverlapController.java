package com.vendex.gateway.api;

import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.workflow.EventAccessService;
import com.vendex.gateway.workflow.GrpcRequestSupport;
import com.vendex.gateway.workflow.VendorDirectory;
import com.vendex.notification.v1.InterestStatus;
import com.vendex.notification.v1.ListInterestsForOverlapRequest;
import com.vendex.notification.v1.NotificationServiceGrpc;
import com.vendex.notification.v1.OverlapInterest;
import com.vendex.overlap.v1.GetOverlapsForVendorRequest;
import com.vendex.overlap.v1.ListSavedOverlapsRequest;
import com.vendex.overlap.v1.Overlap;
import com.vendex.overlap.v1.OverlapServiceGrpc;
import com.vendex.overlap.v1.SaveOverlapRequest;
import com.vendex.overlap.v1.SavedOverlap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/overlaps")
public class OverlapController {

    private final OverlapServiceGrpc.OverlapServiceBlockingStub overlaps;
    private final NotificationServiceGrpc.NotificationServiceBlockingStub notifications;
    private final GatewayProperties properties;
    private final EventAccessService eventAccess;
    private final VendorDirectory vendors;

    public OverlapController(
            OverlapServiceGrpc.OverlapServiceBlockingStub overlaps,
            NotificationServiceGrpc.NotificationServiceBlockingStub notifications,
            GatewayProperties properties,
            EventAccessService eventAccess,
            VendorDirectory vendors) {
        this.overlaps = overlaps;
        this.notifications = notifications;
        this.properties = properties;
        this.eventAccess = eventAccess;
        this.vendors = vendors;
    }

    @GetMapping("/event/{eventId}")
    PageResponse<OverlapResponse> list(
            HttpServletRequest request,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        eventAccess.requireRegisteredVendor(request, eventId);
        var response = overlapStub().getOverlapsForVendor(GetOverlapsForVendorRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setEventId(eventId)
                .setPageSize(pageSize)
                .setPageOffset(pageOffset)
                .build());
        List<String> counterparties = response.getOverlapsList().stream()
                .map(overlap -> counterparty(overlap, principal.userId().toString()))
                .toList();
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request, counterparties, null);
        return new PageResponse<>(response.getOverlapsList().stream()
                .map(overlap -> map(overlap,
                        directory.get(counterparty(overlap, principal.userId().toString()))))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @PostMapping("/{overlapId}/save")
    SavedOverlapResponse save(HttpServletRequest request, @PathVariable String overlapId) {
        GatewayPrincipal principal = vendor(request);
        SavedOverlap saved = overlapStub().saveOverlap(SaveOverlapRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setOverlapId(overlapId)
                .build()).getSavedOverlap();
        String counterpartyId = counterparty(saved.getOverlap(), principal.userId().toString());
        VendorDirectory.PublicVendor counterparty = vendors.findAll(
                request, List.of(counterpartyId), null).get(counterpartyId);
        return map(saved, counterparty);
    }

    @GetMapping("/event/{eventId}/saved")
    PageResponse<SavedOverlapResponse> saved(
            HttpServletRequest request,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        eventAccess.requireRegisteredVendor(request, eventId);
        var response = overlapStub().listSavedOverlaps(ListSavedOverlapsRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setEventId(eventId)
                .setPageSize(pageSize)
                .setPageOffset(pageOffset)
                .build());
        List<String> counterparties = response.getSavedOverlapsList().stream()
                .map(saved -> counterparty(saved.getOverlap(), principal.userId().toString()))
                .toList();
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request, counterparties, null);
        return new PageResponse<>(response.getSavedOverlapsList().stream()
                .map(saved -> map(saved, directory.get(
                        counterparty(saved.getOverlap(), principal.userId().toString()))))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @GetMapping("/{overlapId}/interests")
    PageResponse<InterestResponse> interests(
            HttpServletRequest request,
            @PathVariable String overlapId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        var response = notificationStub().listInterestsForOverlap(
                ListInterestsForOverlapRequest.newBuilder()
                        .setSellerVendorId(principal.userId().toString())
                        .setOverlapId(overlapId)
                        .setPageSize(pageSize)
                        .setPageOffset(pageOffset)
                        .build());
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request,
                response.getInterestsList().stream().map(OverlapInterest::getInterestedVendorId).toList(),
                null);
        return new PageResponse<>(response.getInterestsList().stream()
                .map(interest -> map(interest, directory.get(interest.getInterestedVendorId())))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private OverlapServiceGrpc.OverlapServiceBlockingStub overlapStub() {
        return GrpcRequestSupport.deadline(overlaps, properties);
    }

    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub() {
        return GrpcRequestSupport.deadline(notifications, properties);
    }

    private static String counterparty(Overlap overlap, String callerId) {
        return callerId.equals(overlap.getBuyerVendorId())
                ? overlap.getSellerVendorId()
                : overlap.getBuyerVendorId();
    }

    private static OverlapResponse map(Overlap overlap, VendorDirectory.PublicVendor counterparty) {
        return new OverlapResponse(
                overlap.getId(), overlap.getEventId(), overlap.getBuyerVendorId(),
                overlap.getSellerVendorId(), overlap.getCardId(), overlap.getInventoryItemId(),
                overlap.getWantedCardId(), overlap.getSellerCondition(), overlap.getMinimumCondition(),
                overlap.getAvailableQuantity(), overlap.getQuantityWanted(), overlap.getAskingPrice(),
                overlap.getMaxBuyPrice(), overlap.getInventoryPriority(), overlap.getScore(),
                overlap.getActive(), overlap.getCreatedAtEpochSeconds(),
                overlap.getUpdatedAtEpochSeconds(), counterparty);
    }

    private static SavedOverlapResponse map(
            SavedOverlap saved,
            VendorDirectory.PublicVendor counterparty) {
        return new SavedOverlapResponse(saved.getId(), saved.getVendorId(),
                map(saved.getOverlap(), counterparty), saved.getCreatedAtEpochSeconds());
    }

    private static InterestResponse map(
            OverlapInterest interest,
            VendorDirectory.PublicVendor interestedVendor) {
        return new InterestResponse(
                interest.getId(), interest.getOverlapId(), interest.getInterestedVendorId(),
                interest.getCounterpartyVendorId(), interest.getEventId(), interest.getScore(),
                status(interest.getStatus()), interest.getCreatedAtEpochSeconds(),
                interest.getUpdatedAtEpochSeconds(), interestedVendor);
    }

    private static String status(InterestStatus status) {
        return switch (status) {
            case INTEREST_STATUS_PENDING -> "pending";
            case INTEREST_STATUS_REVEALED -> "revealed";
            case INTEREST_STATUS_DECLINED -> "declined";
            case INTEREST_STATUS_EXPIRED -> "expired";
            default -> "unspecified";
        };
    }

    public record OverlapResponse(
            String id,
            String eventId,
            String buyerVendorId,
            String sellerVendorId,
            String cardId,
            String inventoryItemId,
            String wantedCardId,
            String sellerCondition,
            String minimumCondition,
            int availableQuantity,
            int quantityWanted,
            String askingPrice,
            String maxBuyPrice,
            String inventoryPriority,
            String score,
            boolean active,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds,
            VendorDirectory.PublicVendor counterparty) {}

    public record SavedOverlapResponse(
            String id,
            String vendorId,
            OverlapResponse overlap,
            long createdAtEpochSeconds) {}

    public record InterestResponse(
            String id,
            String overlapId,
            String interestedVendorId,
            String counterpartyVendorId,
            String eventId,
            String score,
            String status,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds,
            VendorDirectory.PublicVendor interestedVendor) {}
}
