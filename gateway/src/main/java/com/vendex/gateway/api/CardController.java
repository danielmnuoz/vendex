package com.vendex.gateway.api;

import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.ListSetsRequest;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.cards.v1.SetSummary;
import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CardController {

    private final CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cards;
    private final GatewayProperties properties;

    public CardController(
            CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cards,
            GatewayProperties properties) {
        this.cards = cards;
        this.properties = properties;
    }

    @GetMapping("/cards/search")
    CardPage search(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String setId,
            @RequestParam(defaultValue = "") String rarity,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "") String pageToken) {
        requireVendor(request);
        var response = stub().searchCards(SearchCardsRequest.newBuilder()
                .setQuery(query)
                .setSetIdFilter(setId)
                .setRarityFilter(rarity)
                .setPageSize(pageSize)
                .setPageToken(pageToken)
                .build());
        return new CardPage(response.getCardsList().stream().map(CardController::map).toList(),
                response.getNextPageToken());
    }

    @GetMapping("/cards/{cardId}")
    CardResponse get(
            HttpServletRequest request,
            @PathVariable @NotBlank String cardId) {
        requireVendor(request);
        return map(stub().getCardById(GetCardByIdRequest.newBuilder().setCardId(cardId).build()).getCard());
    }

    @GetMapping("/sets")
    List<SetResponse> sets(HttpServletRequest request) {
        requireVendor(request);
        return stub().listSets(ListSetsRequest.getDefaultInstance()).getSetsList().stream()
                .map(CardController::map)
                .toList();
    }

    private void requireVendor(HttpServletRequest request) {
        GatewayPrincipal.require(request).requireRole(GatewayRole.VENDOR);
    }

    private CardCatalogServiceGrpc.CardCatalogServiceBlockingStub stub() {
        return cards.withDeadlineAfter(properties.grpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static CardResponse map(Card card) {
        return new CardResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                card.getSetId(),
                card.getSetName(),
                card.getSetSeries(),
                card.getRarity(),
                card.getImageUrl(),
                card.getImageUrlLarge(),
                card.getReleaseDate());
    }

    private static SetResponse map(SetSummary set) {
        return new SetResponse(set.getId(), set.getName(), set.getSeries(), set.getCardCount());
    }

    public record CardPage(List<CardResponse> cards, String nextPageToken) {}

    public record CardResponse(
            String id,
            String externalId,
            String name,
            String setId,
            String setName,
            String setSeries,
            String rarity,
            String imageUrl,
            String imageUrlLarge,
            String releaseDate) {}

    public record SetResponse(String id, String name, String series, int cardCount) {}
}
