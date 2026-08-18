package com.vendex.gateway.api;

import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.workflow.EventAccessService;
import com.vendex.gateway.workflow.GrpcRequestSupport;
import com.vendex.gateway.workflow.VendorDirectory;
import com.vendex.inventory.v1.AddInventoryRequest;
import com.vendex.inventory.v1.BulkImportCSVRequest;
import com.vendex.inventory.v1.CardCondition;
import com.vendex.inventory.v1.InventoryItem;
import com.vendex.inventory.v1.InventoryPriority;
import com.vendex.inventory.v1.InventoryServiceGrpc;
import com.vendex.inventory.v1.ListInventoryByEventRequest;
import com.vendex.inventory.v1.ListInventoryRequest;
import com.vendex.inventory.v1.ListInventoryResponse;
import com.vendex.inventory.v1.RemoveInventoryItemRequest;
import com.vendex.inventory.v1.SearchInventoryForEventRequest;
import com.vendex.inventory.v1.UpdateInventoryItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final GatewayProperties properties;
    private final EventAccessService eventAccess;
    private final VendorDirectory vendors;

    public InventoryController(
            InventoryServiceGrpc.InventoryServiceBlockingStub inventory,
            GatewayProperties properties,
            EventAccessService eventAccess,
            VendorDirectory vendors) {
        this.inventory = inventory;
        this.properties = properties;
        this.eventAccess = eventAccess;
        this.vendors = vendors;
    }

    @GetMapping
    PageResponse<InventoryItemResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String eventId,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        ListInventoryResponse response = eventId.isBlank()
                ? stub().listInventory(ListInventoryRequest.newBuilder()
                        .setVendorId(principal.userId().toString())
                        .setPageSize(pageSize)
                        .setPageOffset(pageOffset)
                        .build())
                : stub().listInventoryByEvent(ListInventoryByEventRequest.newBuilder()
                        .setVendorId(principal.userId().toString())
                        .setEventId(eventId)
                        .setPageSize(pageSize)
                        .setPageOffset(pageOffset)
                        .build());
        return new PageResponse<>(response.getItemsList().stream()
                .map(item -> map(item, null))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @PostMapping
    InventoryItemResponse add(
            HttpServletRequest request,
            @Valid @RequestBody InventoryWriteBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().addInventory(AddInventoryRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setCardId(body.cardId())
                .setEventId(empty(body.eventId()))
                .setCondition(condition(body.condition()))
                .setGradingCompany(empty(body.gradingCompany()))
                .setGrade(empty(body.grade()))
                .setQuantity(body.quantity())
                .setAskingPrice(body.askingPrice())
                .setPriority(priority(body.priority()))
                .build());
        return map(response.getItem(), null);
    }

    @PutMapping("/{itemId}")
    InventoryItemResponse update(
            HttpServletRequest request,
            @PathVariable @NotBlank String itemId,
            @Valid @RequestBody InventoryUpdateBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().updateItem(UpdateInventoryItemRequest.newBuilder()
                .setItemId(itemId)
                .setVendorId(principal.userId().toString())
                .setEventId(empty(body.eventId()))
                .setCondition(condition(body.condition()))
                .setGradingCompany(empty(body.gradingCompany()))
                .setGrade(empty(body.grade()))
                .setQuantity(body.quantity())
                .setAskingPrice(body.askingPrice())
                .setPriority(priority(body.priority()))
                .build());
        return map(response.getItem(), null);
    }

    @DeleteMapping("/{itemId}")
    ResponseEntity<Void> remove(
            HttpServletRequest request,
            @PathVariable @NotBlank String itemId) {
        GatewayPrincipal principal = vendor(request);
        stub().removeItem(RemoveInventoryItemRequest.newBuilder()
                .setItemId(itemId)
                .setVendorId(principal.userId().toString())
                .build());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    ImportResponse importCsv(
            HttpServletRequest request,
            @Valid @RequestBody ImportBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().bulkImportCSV(BulkImportCSVRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setEventId(empty(body.eventId()))
                .setCsvContent(body.csvContent())
                .setDryRun(body.dryRun())
                .build());
        return new ImportResponse(
                response.getImportedItemsList().stream().map(item -> map(item, null)).toList(),
                response.getResolvedRowsList().stream().map(row -> new ResolvedRowResponse(
                        row.getRowNumber(), row.getCardId(), row.getCardName(), row.getSetName(),
                        condition(row.getCondition()), row.getGradingCompany(), row.getGrade(),
                        row.getQuantity(), row.getAskingPrice(), priority(row.getPriority()),
                        row.getConfidence())).toList(),
                response.getIssuesList().stream().map(issue -> new ImportIssueResponse(
                        issue.getRowNumber(), issue.getCardName(), issue.getSetName(), issue.getReason(),
                        issue.getCandidatesList().stream().map(candidate -> new CardCandidateResponse(
                                candidate.getCardId(), candidate.getCardName(), candidate.getSetName(),
                                candidate.getConfidence())).toList())).toList(),
                response.getCommitted());
    }

    @GetMapping("/event/{eventId}/search")
    PageResponse<InventoryItemResponse> searchForEvent(
            HttpServletRequest request,
            @PathVariable @NotBlank String eventId,
            @RequestParam @NotBlank String cardId,
            @RequestParam(name = "condition", required = false) List<String> conditions,
            @RequestParam(defaultValue = "") String maxAskingPrice,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        EventAccessService.VendorRoster roster = eventAccess.requireRegisteredVendor(request, eventId);
        SearchInventoryForEventRequest.Builder query = SearchInventoryForEventRequest.newBuilder()
                .setEventId(eventId)
                .setCardId(cardId)
                .setMaxAskingPrice(maxAskingPrice)
                .setPageSize(pageSize)
                .setPageOffset(pageOffset);
        if (conditions != null) {
            conditions.forEach(value -> query.addConditions(condition(value)));
        }
        var response = stub().searchInventoryForEvent(query.build());
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request,
                response.getItemsList().stream().map(InventoryItem::getVendorId).toList(),
                roster);
        return new PageResponse<>(response.getItemsList().stream()
                .map(item -> map(item, directory.get(item.getVendorId())))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub stub() {
        return GrpcRequestSupport.deadline(inventory, properties);
    }

    private static InventoryItemResponse map(InventoryItem item, VendorDirectory.PublicVendor vendor) {
        return new InventoryItemResponse(
                item.getId(), item.getVendorId(), item.getCardId(), item.getEventId(),
                condition(item.getCondition()), item.getGradingCompany(), item.getGrade(),
                item.getQuantity(), item.getAskingPrice(), priority(item.getPriority()),
                item.getCreatedAtEpochSeconds(), item.getUpdatedAtEpochSeconds(), vendor);
    }

    private static CardCondition condition(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "NM" -> CardCondition.CARD_CONDITION_NM;
            case "LP" -> CardCondition.CARD_CONDITION_LP;
            case "MP" -> CardCondition.CARD_CONDITION_MP;
            case "HP" -> CardCondition.CARD_CONDITION_HP;
            case "DMG" -> CardCondition.CARD_CONDITION_DMG;
            default -> throw new IllegalArgumentException("condition must be one of nm, lp, mp, hp, dmg");
        };
    }

    private static String condition(CardCondition value) {
        return switch (value) {
            case CARD_CONDITION_NM -> "nm";
            case CARD_CONDITION_LP -> "lp";
            case CARD_CONDITION_MP -> "mp";
            case CARD_CONDITION_HP -> "hp";
            case CARD_CONDITION_DMG -> "dmg";
            default -> "unspecified";
        };
    }

    private static InventoryPriority priority(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> InventoryPriority.INVENTORY_PRIORITY_NORMAL;
            case "LIQUIDATE" -> InventoryPriority.INVENTORY_PRIORITY_LIQUIDATE;
            default -> throw new IllegalArgumentException("priority must be normal or liquidate");
        };
    }

    private static String priority(InventoryPriority value) {
        return switch (value) {
            case INVENTORY_PRIORITY_NORMAL -> "normal";
            case INVENTORY_PRIORITY_LIQUIDATE -> "liquidate";
            default -> "unspecified";
        };
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    public record InventoryWriteBody(
            @NotBlank String cardId,
            String eventId,
            @NotBlank String condition,
            @Size(max = 80) String gradingCompany,
            @Size(max = 20) String grade,
            @Min(1) int quantity,
            @NotBlank String askingPrice,
            @NotBlank String priority) {}

    public record InventoryUpdateBody(
            String eventId,
            @NotBlank String condition,
            @Size(max = 80) String gradingCompany,
            @Size(max = 20) String grade,
            @Min(1) int quantity,
            @NotBlank String askingPrice,
            @NotBlank String priority) {}

    public record ImportBody(
            String eventId,
            @NotBlank @Size(max = 5_000_000) String csvContent,
            boolean dryRun) {}

    public record InventoryItemResponse(
            String id,
            String vendorId,
            String cardId,
            String eventId,
            String condition,
            String gradingCompany,
            String grade,
            int quantity,
            String askingPrice,
            String priority,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds,
            VendorDirectory.PublicVendor vendor) {}

    public record ImportResponse(
            List<InventoryItemResponse> importedItems,
            List<ResolvedRowResponse> resolvedRows,
            List<ImportIssueResponse> issues,
            boolean committed) {}

    public record ResolvedRowResponse(
            int rowNumber,
            String cardId,
            String cardName,
            String setName,
            String condition,
            String gradingCompany,
            String grade,
            int quantity,
            String askingPrice,
            String priority,
            double confidence) {}

    public record ImportIssueResponse(
            int rowNumber,
            String cardName,
            String setName,
            String reason,
            List<CardCandidateResponse> candidates) {}

    public record CardCandidateResponse(
            String cardId,
            String cardName,
            String setName,
            double confidence) {}
}
