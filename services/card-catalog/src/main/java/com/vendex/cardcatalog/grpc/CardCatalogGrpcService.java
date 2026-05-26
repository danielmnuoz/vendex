package com.vendex.cardcatalog.grpc;

import com.vendex.cardcatalog.domain.SetSummary;
import com.vendex.cardcatalog.service.CardService;
import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.GetCardByIdResponse;
import com.vendex.cards.v1.GetCardsByIdsRequest;
import com.vendex.cards.v1.GetCardsByIdsResponse;
import com.vendex.cards.v1.ListSetsRequest;
import com.vendex.cards.v1.ListSetsResponse;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.cards.v1.SearchCardsResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC surface for {@link CardService}. Pure adapter — every method
 * converts proto request → domain call → proto response, with exceptions
 * routed through {@link ErrorMapper}.
 */
@GrpcService
public class CardCatalogGrpcService extends CardCatalogServiceGrpc.CardCatalogServiceImplBase {

    private final CardService service;

    public CardCatalogGrpcService(CardService service) {
        this.service = service;
    }

    @Override
    public void searchCards(SearchCardsRequest request, StreamObserver<SearchCardsResponse> obs) {
        try {
            CardService.SearchResult result = service.searchCards(
                    request.getQuery(),
                    request.getSetIdFilter(),
                    request.getRarityFilter(),
                    request.getPageSize(),
                    request.getPageToken()
            );
            SearchCardsResponse.Builder out = SearchCardsResponse.newBuilder();
            for (com.vendex.cardcatalog.domain.Card c : result.cards()) {
                out.addCards(toProto(c));
            }
            if (result.nextPageToken() != null) {
                out.setNextPageToken(result.nextPageToken());
            }
            obs.onNext(out.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getCardById(GetCardByIdRequest request, StreamObserver<GetCardByIdResponse> obs) {
        try {
            com.vendex.cardcatalog.domain.Card card = service.getById(request.getCardId());
            obs.onNext(GetCardByIdResponse.newBuilder().setCard(toProto(card)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getCardsByIds(GetCardsByIdsRequest request, StreamObserver<GetCardsByIdsResponse> obs) {
        try {
            var cards = service.getByIds(request.getCardIdsList());
            GetCardsByIdsResponse.Builder out = GetCardsByIdsResponse.newBuilder();
            for (com.vendex.cardcatalog.domain.Card c : cards) {
                out.addCards(toProto(c));
            }
            obs.onNext(out.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listSets(ListSetsRequest request, StreamObserver<ListSetsResponse> obs) {
        try {
            ListSetsResponse.Builder out = ListSetsResponse.newBuilder();
            for (SetSummary s : service.listSets()) {
                out.addSets(com.vendex.cards.v1.SetSummary.newBuilder()
                        .setId(nullToEmpty(s.id()))
                        .setName(nullToEmpty(s.name()))
                        .setSeries(nullToEmpty(s.series()))
                        .setCardCount(s.cardCount())
                        .build());
            }
            obs.onNext(out.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    private static Card toProto(com.vendex.cardcatalog.domain.Card c) {
        return Card.newBuilder()
                .setId(c.id().toString())
                .setExternalId(c.externalId())
                .setName(c.name())
                .setSetId(c.setId())
                .setSetName(c.setName())
                .setSetSeries(nullToEmpty(c.setSeries()))
                .setRarity(nullToEmpty(c.rarity()))
                .setImageUrl(nullToEmpty(c.imageUrl()))
                .setImageUrlLarge(nullToEmpty(c.imageUrlLarge()))
                .setReleaseDate(c.releaseDate() == null ? "" : c.releaseDate().toString())
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
