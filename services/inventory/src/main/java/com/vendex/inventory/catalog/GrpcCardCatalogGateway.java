package com.vendex.inventory.catalog;

import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.inventory.service.InventoryExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcCardCatalogGateway implements CardCatalogGateway {

    private static final long DEADLINE_SECONDS = 3;

    private final CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cards;

    public GrpcCardCatalogGateway(CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cards) {
        this.cards = cards;
    }

    @Override
    public CanonicalCard get(UUID cardId) {
        try {
            Card card = cards.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getCardById(GetCardByIdRequest.newBuilder()
                            .setCardId(cardId.toString())
                            .build())
                    .getCard();
            return toDomain(card);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new InventoryExceptions.CardNotFoundException(cardId.toString());
            }
            throw new InventoryExceptions.DependencyUnavailableException(
                    "card catalog lookup failed", e);
        }
    }

    @Override
    public List<CanonicalCard> search(String cardName, int limit) {
        try {
            return cards.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .searchCards(SearchCardsRequest.newBuilder()
                            .setQuery(cardName)
                            .setPageSize(limit)
                            .build())
                    .getCardsList()
                    .stream()
                    .map(GrpcCardCatalogGateway::toDomain)
                    .toList();
        } catch (StatusRuntimeException e) {
            throw new InventoryExceptions.DependencyUnavailableException(
                    "card catalog search failed", e);
        }
    }

    private static CanonicalCard toDomain(Card card) {
        try {
            return new CanonicalCard(
                    UUID.fromString(card.getId()),
                    card.getExternalId(),
                    card.getName(),
                    card.getSetId(),
                    card.getSetName());
        } catch (IllegalArgumentException e) {
            throw new InventoryExceptions.DependencyUnavailableException(
                    "card catalog returned a malformed canonical ID", e);
        }
    }
}
