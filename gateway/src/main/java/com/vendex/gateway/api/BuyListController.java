package com.vendex.gateway.api;

import com.vendex.buylist.v1.AddWantedCardRequest;
import com.vendex.buylist.v1.BuyListServiceGrpc;
import com.vendex.buylist.v1.CardCondition;
import com.vendex.buylist.v1.ListBuyListRequest;
import com.vendex.buylist.v1.ListBuyListsForEventRequest;
import com.vendex.buylist.v1.RemoveWantedCardRequest;
import com.vendex.buylist.v1.UpdateWantedCardRequest;
import com.vendex.buylist.v1.WantedCard;
import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.workflow.EventAccessService;
import com.vendex.gateway.workflow.GrpcRequestSupport;
import com.vendex.gateway.workflow.VendorDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/buylist")
public class BuyListController {

    private final BuyListServiceGrpc.BuyListServiceBlockingStub buyList;
    private final GatewayProperties properties;
    private final EventAccessService eventAccess;
    private final VendorDirectory vendors;

    public BuyListController(
            BuyListServiceGrpc.BuyListServiceBlockingStub buyList,
            GatewayProperties properties,
            EventAccessService eventAccess,
            VendorDirectory vendors) {
        this.buyList = buyList;
        this.properties = properties;
        this.eventAccess = eventAccess;
        this.vendors = vendors;
    }

    @GetMapping
    PageResponse<WantedCardResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().listBuyList(ListBuyListRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setPageSize(pageSize)
                .setPageOffset(pageOffset)
                .build());
        return new PageResponse<>(response.getWantedCardsList().stream()
                .map(card -> map(card, null))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @PostMapping
    WantedCardResponse add(
            HttpServletRequest request,
            @Valid @RequestBody WantedCardWriteBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().addWantedCard(AddWantedCardRequest.newBuilder()
                .setVendorId(principal.userId().toString())
                .setCardId(body.cardId())
                .setMinimumCondition(condition(body.minimumCondition()))
                .setMaxBuyPrice(body.maxBuyPrice())
                .setQuantityWanted(body.quantityWanted())
                .build());
        return map(response.getWantedCard(), null);
    }

    @PutMapping("/{wantedCardId}")
    WantedCardResponse update(
            HttpServletRequest request,
            @PathVariable @NotBlank String wantedCardId,
            @Valid @RequestBody WantedCardUpdateBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().updateWantedCard(UpdateWantedCardRequest.newBuilder()
                .setWantedCardId(wantedCardId)
                .setVendorId(principal.userId().toString())
                .setMinimumCondition(condition(body.minimumCondition()))
                .setMaxBuyPrice(body.maxBuyPrice())
                .setQuantityWanted(body.quantityWanted())
                .build());
        return map(response.getWantedCard(), null);
    }

    @DeleteMapping("/{wantedCardId}")
    ResponseEntity<Void> remove(
            HttpServletRequest request,
            @PathVariable @NotBlank String wantedCardId) {
        GatewayPrincipal principal = vendor(request);
        stub().removeWantedCard(RemoveWantedCardRequest.newBuilder()
                .setWantedCardId(wantedCardId)
                .setVendorId(principal.userId().toString())
                .build());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/event/{eventId}")
    PageResponse<WantedCardResponse> listForEvent(
            HttpServletRequest request,
            @PathVariable @NotBlank String eventId,
            @RequestParam(defaultValue = "") String cardId,
            @RequestParam(name = "minimumCondition", required = false) List<String> conditions,
            @RequestParam(defaultValue = "") String minMaxBuyPrice,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        EventAccessService.VendorRoster roster = eventAccess.requireRegisteredVendor(request, eventId);
        ListBuyListsForEventRequest.Builder query = ListBuyListsForEventRequest.newBuilder()
                .setEventId(eventId)
                .setCardId(cardId)
                .setMinMaxBuyPrice(minMaxBuyPrice)
                .setPageSize(pageSize)
                .setPageOffset(pageOffset);
        if (conditions != null) {
            conditions.forEach(value -> query.addMinimumConditions(condition(value)));
        }
        var response = stub().listBuyListsForEvent(query.build());
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request,
                response.getWantedCardsList().stream().map(WantedCard::getVendorId).toList(),
                roster);
        return new PageResponse<>(response.getWantedCardsList().stream()
                .map(card -> map(card, directory.get(card.getVendorId())))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private BuyListServiceGrpc.BuyListServiceBlockingStub stub() {
        return GrpcRequestSupport.deadline(buyList, properties);
    }

    private static WantedCardResponse map(WantedCard card, VendorDirectory.PublicVendor vendor) {
        return new WantedCardResponse(
                card.getId(), card.getVendorId(), card.getCardId(),
                condition(card.getMinimumCondition()), card.getMaxBuyPrice(),
                card.getQuantityWanted(), card.getCreatedAtEpochSeconds(),
                card.getUpdatedAtEpochSeconds(), vendor);
    }

    private static CardCondition condition(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "NM" -> CardCondition.CARD_CONDITION_NM;
            case "LP" -> CardCondition.CARD_CONDITION_LP;
            case "MP" -> CardCondition.CARD_CONDITION_MP;
            case "HP" -> CardCondition.CARD_CONDITION_HP;
            case "DMG" -> CardCondition.CARD_CONDITION_DMG;
            default -> throw new IllegalArgumentException(
                    "minimumCondition must be one of nm, lp, mp, hp, dmg");
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

    public record WantedCardWriteBody(
            @NotBlank String cardId,
            @NotBlank String minimumCondition,
            @NotBlank String maxBuyPrice,
            @Min(1) int quantityWanted) {}

    public record WantedCardUpdateBody(
            @NotBlank String minimumCondition,
            @NotBlank String maxBuyPrice,
            @Min(1) int quantityWanted) {}

    public record WantedCardResponse(
            String id,
            String vendorId,
            String cardId,
            String minimumCondition,
            String maxBuyPrice,
            int quantityWanted,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds,
            VendorDirectory.PublicVendor vendor) {}
}
