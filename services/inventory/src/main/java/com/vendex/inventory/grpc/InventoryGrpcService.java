package com.vendex.inventory.grpc;

import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.service.InventoryService;
import com.vendex.inventory.v1.AddInventoryRequest;
import com.vendex.inventory.v1.AddInventoryResponse;
import com.vendex.inventory.v1.BulkImportCSVRequest;
import com.vendex.inventory.v1.BulkImportCSVResponse;
import com.vendex.inventory.v1.CardMatchCandidate;
import com.vendex.inventory.v1.CsvImportIssue;
import com.vendex.inventory.v1.CsvResolvedRow;
import com.vendex.inventory.v1.InventoryServiceGrpc;
import com.vendex.inventory.v1.ListInventoryByEventRequest;
import com.vendex.inventory.v1.ListInventoryRequest;
import com.vendex.inventory.v1.ListInventoryResponse;
import com.vendex.inventory.v1.RemoveInventoryItemRequest;
import com.vendex.inventory.v1.RemoveInventoryItemResponse;
import com.vendex.inventory.v1.SearchInventoryForEventRequest;
import com.vendex.inventory.v1.UpdateInventoryItemRequest;
import com.vendex.inventory.v1.UpdateInventoryItemResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventory;

    public InventoryGrpcService(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Override
    public void addInventory(AddInventoryRequest request, StreamObserver<AddInventoryResponse> obs) {
        try {
            InventoryItem item = inventory.add(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getCardId(), "card_id"),
                    input(request.getEventId(), request.getCondition(), request.getGradingCompany(),
                            request.getGrade(), request.getQuantity(), request.getAskingPrice(),
                            request.getPriority()));
            obs.onNext(AddInventoryResponse.newBuilder().setItem(toProto(item)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void updateItem(UpdateInventoryItemRequest request,
                           StreamObserver<UpdateInventoryItemResponse> obs) {
        try {
            InventoryItem item = inventory.update(
                    uuid(request.getItemId(), "item_id"),
                    uuid(request.getVendorId(), "vendor_id"),
                    input(request.getEventId(), request.getCondition(), request.getGradingCompany(),
                            request.getGrade(), request.getQuantity(), request.getAskingPrice(),
                            request.getPriority()));
            obs.onNext(UpdateInventoryItemResponse.newBuilder().setItem(toProto(item)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void removeItem(RemoveInventoryItemRequest request,
                           StreamObserver<RemoveInventoryItemResponse> obs) {
        try {
            InventoryItem item = inventory.remove(
                    uuid(request.getItemId(), "item_id"),
                    uuid(request.getVendorId(), "vendor_id"));
            obs.onNext(RemoveInventoryItemResponse.newBuilder()
                    .setRemovedItem(toProto(item)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listInventory(ListInventoryRequest request,
                              StreamObserver<ListInventoryResponse> obs) {
        try {
            respond(inventory.list(uuid(request.getVendorId(), "vendor_id"),
                    request.getPageSize(), request.getPageOffset()), obs);
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listInventoryByEvent(ListInventoryByEventRequest request,
                                     StreamObserver<ListInventoryResponse> obs) {
        try {
            respond(inventory.listForEvent(
                    uuid(request.getVendorId(), "vendor_id"),
                    uuid(request.getEventId(), "event_id"),
                    request.getPageSize(), request.getPageOffset()), obs);
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void searchInventoryForEvent(SearchInventoryForEventRequest request,
                                        StreamObserver<ListInventoryResponse> obs) {
        try {
            List<com.vendex.inventory.domain.CardCondition> conditions = request.getConditionsList()
                    .stream().map(InventoryGrpcService::condition).toList();
            respond(inventory.searchForEvent(
                    uuid(request.getEventId(), "event_id"),
                    uuid(request.getCardId(), "card_id"),
                    conditions,
                    optionalDecimal(request.getMaxAskingPrice(), "max_asking_price"),
                    request.getPageSize(), request.getPageOffset()), obs);
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void bulkImportCSV(BulkImportCSVRequest request,
                              StreamObserver<BulkImportCSVResponse> obs) {
        try {
            InventoryService.BulkImportResult result = inventory.bulkImport(
                    uuid(request.getVendorId(), "vendor_id"),
                    optionalUuid(request.getEventId(), "event_id"),
                    request.getCsvContent(), request.getDryRun());
            BulkImportCSVResponse.Builder response = BulkImportCSVResponse.newBuilder()
                    .setCommitted(result.committed());
            result.importedItems().forEach(item -> response.addImportedItems(toProto(item)));
            result.resolvedRows().forEach(row -> response.addResolvedRows(toProto(row)));
            result.issues().forEach(issue -> response.addIssues(toProto(issue)));
            obs.onNext(response.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    private static InventoryItemInput input(
            String eventId,
            com.vendex.inventory.v1.CardCondition condition,
            String gradingCompany,
            String grade,
            int quantity,
            String askingPrice,
            com.vendex.inventory.v1.InventoryPriority priority) {
        return new InventoryItemInput(
                optionalUuid(eventId, "event_id"),
                condition(condition),
                gradingCompany,
                optionalDecimal(grade, "grade"),
                quantity,
                requiredDecimal(askingPrice, "asking_price"),
                priority(priority));
    }

    private static void respond(InventoryService.Page page, StreamObserver<ListInventoryResponse> obs) {
        ListInventoryResponse.Builder response = ListInventoryResponse.newBuilder()
                .setHasMore(page.hasMore())
                .setNextPageOffset(page.nextPageOffset());
        page.items().forEach(item -> response.addItems(toProto(item)));
        obs.onNext(response.build());
        obs.onCompleted();
    }

    private static com.vendex.inventory.v1.InventoryItem toProto(InventoryItem item) {
        return com.vendex.inventory.v1.InventoryItem.newBuilder()
                .setId(item.id().toString())
                .setVendorId(item.vendorId().toString())
                .setCardId(item.cardId().toString())
                .setEventId(item.eventId() == null ? "" : item.eventId().toString())
                .setCondition(condition(item.condition()))
                .setGradingCompany(nullToEmpty(item.gradingCompany()))
                .setGrade(decimal(item.grade()))
                .setQuantity(item.quantity())
                .setAskingPrice(decimal(item.askingPrice()))
                .setPriority(priority(item.priority()))
                .setCreatedAtEpochSeconds(item.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(item.updatedAt().getEpochSecond())
                .build();
    }

    private static CsvResolvedRow toProto(InventoryService.ResolvedRow row) {
        return CsvResolvedRow.newBuilder()
                .setRowNumber(row.rowNumber())
                .setCardId(row.cardId().toString())
                .setCardName(row.cardName())
                .setSetName(row.setName())
                .setCondition(condition(row.input().condition()))
                .setGradingCompany(nullToEmpty(row.input().gradingCompany()))
                .setGrade(decimal(row.input().grade()))
                .setQuantity(row.input().quantity())
                .setAskingPrice(decimal(row.input().askingPrice()))
                .setPriority(priority(row.input().priority()))
                .setConfidence(row.confidence())
                .build();
    }

    private static CsvImportIssue toProto(InventoryService.ImportIssue issue) {
        CsvImportIssue.Builder out = CsvImportIssue.newBuilder()
                .setRowNumber(issue.rowNumber())
                .setCardName(nullToEmpty(issue.cardName()))
                .setSetName(nullToEmpty(issue.setName()))
                .setReason(issue.reason());
        issue.candidates().forEach(candidate -> out.addCandidates(CardMatchCandidate.newBuilder()
                .setCardId(candidate.cardId().toString())
                .setCardName(candidate.cardName())
                .setSetName(candidate.setName())
                .setConfidence(candidate.confidence())
                .build()));
        return out.build();
    }

    private static com.vendex.inventory.domain.CardCondition condition(
            com.vendex.inventory.v1.CardCondition value) {
        return switch (value) {
            case CARD_CONDITION_NM -> com.vendex.inventory.domain.CardCondition.NM;
            case CARD_CONDITION_LP -> com.vendex.inventory.domain.CardCondition.LP;
            case CARD_CONDITION_MP -> com.vendex.inventory.domain.CardCondition.MP;
            case CARD_CONDITION_HP -> com.vendex.inventory.domain.CardCondition.HP;
            case CARD_CONDITION_DMG -> com.vendex.inventory.domain.CardCondition.DMG;
            default -> throw new IllegalArgumentException("condition is required");
        };
    }

    private static com.vendex.inventory.v1.CardCondition condition(
            com.vendex.inventory.domain.CardCondition value) {
        return com.vendex.inventory.v1.CardCondition.valueOf("CARD_CONDITION_" + value.name());
    }

    private static com.vendex.inventory.domain.InventoryPriority priority(
            com.vendex.inventory.v1.InventoryPriority value) {
        return switch (value) {
            case INVENTORY_PRIORITY_NORMAL -> com.vendex.inventory.domain.InventoryPriority.NORMAL;
            case INVENTORY_PRIORITY_LIQUIDATE -> com.vendex.inventory.domain.InventoryPriority.LIQUIDATE;
            default -> throw new IllegalArgumentException("priority is required");
        };
    }

    private static com.vendex.inventory.v1.InventoryPriority priority(
            com.vendex.inventory.domain.InventoryPriority value) {
        return com.vendex.inventory.v1.InventoryPriority.valueOf("INVENTORY_PRIORITY_" + value.name());
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
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
