package com.vendex.inventory.catalog;

import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.GetCardByIdResponse;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.cards.v1.SearchCardsResponse;
import com.vendex.inventory.service.InventoryExceptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcCardCatalogGatewayTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void close() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void readsCanonicalCardsByUuidAndSearchesByName() throws IOException {
        UUID cardId = UUID.randomUUID();
        GrpcCardCatalogGateway gateway = gateway(new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
            @Override
            public void getCardById(GetCardByIdRequest request,
                                    StreamObserver<GetCardByIdResponse> observer) {
                assertThat(request.getCardId()).isEqualTo(cardId.toString());
                observer.onNext(GetCardByIdResponse.newBuilder().setCard(card(cardId)).build());
                observer.onCompleted();
            }

            @Override
            public void searchCards(SearchCardsRequest request,
                                    StreamObserver<SearchCardsResponse> observer) {
                assertThat(request.getQuery()).isEqualTo("Pikachu");
                assertThat(request.getPageSize()).isEqualTo(7);
                observer.onNext(SearchCardsResponse.newBuilder().addCards(card(cardId)).build());
                observer.onCompleted();
            }
        });

        assertThat(gateway.get(cardId))
                .extracting(CardCatalogGateway.CanonicalCard::id,
                        CardCatalogGateway.CanonicalCard::externalId,
                        CardCatalogGateway.CanonicalCard::name,
                        CardCatalogGateway.CanonicalCard::setName)
                .containsExactly(cardId, "sv03-001", "Pikachu", "Obsidian Flames");
        assertThat(gateway.search("Pikachu", 7))
                .singleElement()
                .extracting(CardCatalogGateway.CanonicalCard::id)
                .isEqualTo(cardId);
    }

    @Test
    void mapsCatalogNotFoundToTheInventoryBoundary() throws IOException {
        UUID cardId = UUID.randomUUID();
        GrpcCardCatalogGateway gateway = gateway(new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
            @Override
            public void getCardById(GetCardByIdRequest request,
                                    StreamObserver<GetCardByIdResponse> observer) {
                observer.onError(Status.NOT_FOUND.asRuntimeException());
            }
        });

        assertThatThrownBy(() -> gateway.get(cardId))
                .isInstanceOf(InventoryExceptions.CardNotFoundException.class);
    }

    @Test
    void rejectsMalformedCanonicalIdsFromTheCatalog() throws IOException {
        GrpcCardCatalogGateway gateway = gateway(new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
            @Override
            public void searchCards(SearchCardsRequest request,
                                    StreamObserver<SearchCardsResponse> observer) {
                observer.onNext(SearchCardsResponse.newBuilder().addCards(Card.newBuilder()
                        .setId("not-a-uuid")
                        .setName("Pikachu")
                        .build()).build());
                observer.onCompleted();
            }
        });

        assertThatThrownBy(() -> gateway.search("Pikachu", 10))
                .isInstanceOf(InventoryExceptions.DependencyUnavailableException.class)
                .hasMessageContaining("malformed canonical ID");
    }

    private GrpcCardCatalogGateway gateway(CardCatalogServiceGrpc.CardCatalogServiceImplBase service)
            throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return new GrpcCardCatalogGateway(CardCatalogServiceGrpc.newBlockingStub(channel));
    }

    private static Card card(UUID id) {
        return Card.newBuilder()
                .setId(id.toString())
                .setExternalId("sv03-001")
                .setName("Pikachu")
                .setSetId("sv03")
                .setSetName("Obsidian Flames")
                .build();
    }
}
