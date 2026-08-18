package com.vendex.buylist.grpc;

import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import com.vendex.buylist.service.BuyListService;
import com.vendex.buylist.v1.AddWantedCardRequest;
import com.vendex.buylist.v1.AddWantedCardResponse;
import com.vendex.buylist.v1.BuyListServiceGrpc;
import com.vendex.buylist.v1.ListBuyListRequest;
import com.vendex.buylist.v1.ListBuyListsForEventRequest;
import com.vendex.buylist.v1.ListWantedCardsResponse;
import com.vendex.buylist.v1.RemoveWantedCardRequest;
import com.vendex.buylist.v1.RemoveWantedCardResponse;
import com.vendex.buylist.v1.UpdateWantedCardRequest;
import com.vendex.buylist.v1.UpdateWantedCardResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@GrpcService
public class BuyListGrpcService extends BuyListServiceGrpc.BuyListServiceImplBase {

    private final BuyListService buyList;

    public BuyListGrpcService(BuyListService buyList) {
        this.buyList = buyList;
    }

    @Override
    public void addWantedCard(AddWantedCardRequest request,
                              StreamObserver<AddWantedCardResponse> observer) {
        try {
            WantedCard wantedCard = buyList.add(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getCardId(), "card_id"),
                    input(request.getMinimumCondition(), request.getMaxBuyPrice(),
                            request.getQuantityWanted()));
            observer.onNext(AddWantedCardResponse.newBuilder()
                    .setWantedCard(toProto(wantedCard)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void updateWantedCard(UpdateWantedCardRequest request,
                                 StreamObserver<UpdateWantedCardResponse> observer) {
        try {
            WantedCard wantedCard = buyList.update(
                    uuid(request.getWantedCardId(), "wanted_card_id"),
                    uuid(request.getVendorId(), "vendor_id"),
                    input(request.getMinimumCondition(), request.getMaxBuyPrice(),
                            request.getQuantityWanted()));
            observer.onNext(UpdateWantedCardResponse.newBuilder()
                    .setWantedCard(toProto(wantedCard)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void removeWantedCard(RemoveWantedCardRequest request,
                                 StreamObserver<RemoveWantedCardResponse> observer) {
        try {
            WantedCard wantedCard = buyList.remove(
                    uuid(request.getWantedCardId(), "wanted_card_id"),
                    uuid(request.getVendorId(), "vendor_id"));
            observer.onNext(RemoveWantedCardResponse.newBuilder()
                    .setRemovedWantedCard(toProto(wantedCard)).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listBuyList(ListBuyListRequest request,
                            StreamObserver<ListWantedCardsResponse> observer) {
        try {
            respond(buyList.list(uuid(request.getVendorId(), "vendor_id"),
                    request.getPageSize(), request.getPageOffset()), observer);
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listBuyListsForEvent(ListBuyListsForEventRequest request,
                                     StreamObserver<ListWantedCardsResponse> observer) {
        try {
            List<com.vendex.buylist.domain.CardCondition> conditions =
                    request.getMinimumConditionsList().stream()
                            .map(BuyListGrpcService::condition).toList();
            respond(buyList.listForEvent(
                    uuid(request.getEventId(), "event_id"),
                    optionalUuid(request.getCardId(), "card_id"),
                    conditions,
                    optionalDecimal(request.getMinMaxBuyPrice(), "min_max_buy_price"),
                    request.getPageSize(),
                    request.getPageOffset()), observer);
        } catch (Exception e) {
            observer.onError(ErrorMapper.map(e));
        }
    }

    private static WantedCardInput input(
            com.vendex.buylist.v1.CardCondition minimumCondition,
            String maxBuyPrice,
            int quantityWanted) {
        return new WantedCardInput(condition(minimumCondition),
                requiredDecimal(maxBuyPrice, "max_buy_price"), quantityWanted);
    }

    private static void respond(BuyListService.Page page,
                                StreamObserver<ListWantedCardsResponse> observer) {
        ListWantedCardsResponse.Builder response = ListWantedCardsResponse.newBuilder()
                .setHasMore(page.hasMore())
                .setNextPageOffset(page.nextPageOffset());
        page.wantedCards().forEach(wantedCard -> response.addWantedCards(toProto(wantedCard)));
        observer.onNext(response.build());
        observer.onCompleted();
    }

    private static com.vendex.buylist.v1.WantedCard toProto(WantedCard wantedCard) {
        return com.vendex.buylist.v1.WantedCard.newBuilder()
                .setId(wantedCard.id().toString())
                .setVendorId(wantedCard.vendorId().toString())
                .setCardId(wantedCard.cardId().toString())
                .setMinimumCondition(condition(wantedCard.minimumCondition()))
                .setMaxBuyPrice(decimal(wantedCard.maxBuyPrice()))
                .setQuantityWanted(wantedCard.quantityWanted())
                .setCreatedAtEpochSeconds(wantedCard.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(wantedCard.updatedAt().getEpochSecond())
                .build();
    }

    private static com.vendex.buylist.domain.CardCondition condition(
            com.vendex.buylist.v1.CardCondition value) {
        return switch (value) {
            case CARD_CONDITION_NM -> com.vendex.buylist.domain.CardCondition.NM;
            case CARD_CONDITION_LP -> com.vendex.buylist.domain.CardCondition.LP;
            case CARD_CONDITION_MP -> com.vendex.buylist.domain.CardCondition.MP;
            case CARD_CONDITION_HP -> com.vendex.buylist.domain.CardCondition.HP;
            case CARD_CONDITION_DMG -> com.vendex.buylist.domain.CardCondition.DMG;
            default -> throw new IllegalArgumentException("minimum_condition is required");
        };
    }

    private static com.vendex.buylist.v1.CardCondition condition(
            com.vendex.buylist.domain.CardCondition value) {
        return com.vendex.buylist.v1.CardCondition.valueOf("CARD_CONDITION_" + value.name());
    }

    private static UUID uuid(String value, String field) {
        UUID result = optionalUuid(value, field);
        if (result == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return result;
    }

    private static UUID optionalUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static BigDecimal requiredDecimal(String value, String field) {
        BigDecimal parsed = optionalDecimal(value, field);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return parsed;
    }

    private static BigDecimal optionalDecimal(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number");
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
