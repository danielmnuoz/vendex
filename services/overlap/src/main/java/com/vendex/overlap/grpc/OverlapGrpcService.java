package com.vendex.overlap.grpc;

import com.vendex.overlap.domain.SavedOverlap;
import com.vendex.overlap.service.OverlapQueryService;
import com.vendex.overlap.v1.GetOverlapsForVendorRequest;
import com.vendex.overlap.v1.ListOverlapsResponse;
import com.vendex.overlap.v1.ListSavedOverlapsRequest;
import com.vendex.overlap.v1.ListSavedOverlapsResponse;
import com.vendex.overlap.v1.OverlapServiceGrpc;
import com.vendex.overlap.v1.SaveOverlapRequest;
import com.vendex.overlap.v1.SaveOverlapResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
public class OverlapGrpcService extends OverlapServiceGrpc.OverlapServiceImplBase {

    private final OverlapQueryService overlaps;

    public OverlapGrpcService(OverlapQueryService overlaps) {
        this.overlaps = overlaps;
    }

    @Override
    public void getOverlapsForVendor(
            GetOverlapsForVendorRequest request,
            StreamObserver<ListOverlapsResponse> observer) {
        try {
            OverlapQueryService.OverlapPage page = overlaps.getForVendor(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getEventId(), "event_id"),
                    request.getPageSize(), request.getPageOffset());
            ListOverlapsResponse.Builder response = ListOverlapsResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.overlaps().forEach(value -> response.addOverlaps(toProto(value)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void saveOverlap(
            SaveOverlapRequest request, StreamObserver<SaveOverlapResponse> observer) {
        try {
            SavedOverlap saved = overlaps.save(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getOverlapId(), "overlap_id"));
            observer.onNext(SaveOverlapResponse.newBuilder()
                    .setSavedOverlap(toProto(saved)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listSavedOverlaps(
            ListSavedOverlapsRequest request,
            StreamObserver<ListSavedOverlapsResponse> observer) {
        try {
            OverlapQueryService.SavedPage page = overlaps.listSaved(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getEventId(), "event_id"),
                    request.getPageSize(), request.getPageOffset());
            ListSavedOverlapsResponse.Builder response = ListSavedOverlapsResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.saved().forEach(value -> response.addSavedOverlaps(toProto(value)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    private static com.vendex.overlap.v1.Overlap toProto(
            com.vendex.overlap.domain.Overlap value) {
        return com.vendex.overlap.v1.Overlap.newBuilder()
                .setId(value.id().toString())
                .setEventId(value.eventId().toString())
                .setBuyerVendorId(value.buyerVendorId().toString())
                .setSellerVendorId(value.sellerVendorId().toString())
                .setCardId(value.cardId().toString())
                .setInventoryItemId(value.inventoryItemId().toString())
                .setWantedCardId(value.wantedCardId().toString())
                .setSellerCondition(value.sellerCondition().name())
                .setMinimumCondition(value.minimumCondition().name())
                .setAvailableQuantity(value.availableQuantity())
                .setQuantityWanted(value.quantityWanted())
                .setAskingPrice(decimal(value.askingPrice()))
                .setMaxBuyPrice(decimal(value.maxBuyPrice()))
                .setInventoryPriority(value.inventoryPriority().name().toLowerCase())
                .setScore(decimal(value.score()))
                .setActive(value.active())
                .setCreatedAtEpochSeconds(value.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(value.updatedAt().getEpochSecond())
                .build();
    }

    private static com.vendex.overlap.v1.SavedOverlap toProto(SavedOverlap value) {
        return com.vendex.overlap.v1.SavedOverlap.newBuilder()
                .setId(value.id().toString())
                .setVendorId(value.vendorId().toString())
                .setOverlap(toProto(value.overlap()))
                .setCreatedAtEpochSeconds(value.createdAt().getEpochSecond())
                .build();
    }

    private static UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
