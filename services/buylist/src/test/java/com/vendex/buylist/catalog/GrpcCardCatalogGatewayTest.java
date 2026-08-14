package com.vendex.buylist.catalog;

import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.GetCardByIdResponse;
import com.vendex.buylist.service.BuyListExceptions;
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
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Test
    void getsCanonicalCardByUuid() throws IOException {
        UUID cardId = UUID.randomUUID();
        GrpcCardCatalogGateway gateway = gateway(new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
            @Override
            public void getCardById(GetCardByIdRequest request,
                                    StreamObserver<GetCardByIdResponse> observer) {
                assertThat(request.getCardId()).isEqualTo(cardId.toString());
                observer.onNext(GetCardByIdResponse.newBuilder().setCard(Card.newBuilder()
                        .setId(cardId.toString())
                        .setExternalId("sv03-001")
                        .setName("Pikachu")
                        .setSetId("sv03")
                        .setSetName("Obsidian Flames")
                        .build()).build());
                observer.onCompleted();
            }
        });

        assertThat(gateway.get(cardId))
                .extracting(CardCatalogGateway.CanonicalCard::id,
                        CardCatalogGateway.CanonicalCard::name)
                .containsExactly(cardId, "Pikachu");
    }

    @Test
    void mapsNotFoundAndMalformedResponses() throws IOException {
        UUID cardId = UUID.randomUUID();
        GrpcCardCatalogGateway notFound = gateway(
                new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
                    @Override
                    public void getCardById(GetCardByIdRequest request,
                                            StreamObserver<GetCardByIdResponse> observer) {
                        observer.onError(Status.NOT_FOUND.asRuntimeException());
                    }
                });

        assertThatThrownBy(() -> notFound.get(cardId))
                .isInstanceOf(BuyListExceptions.CardNotFoundException.class);

        close();
        GrpcCardCatalogGateway malformed = gateway(
                new CardCatalogServiceGrpc.CardCatalogServiceImplBase() {
                    @Override
                    public void getCardById(GetCardByIdRequest request,
                                            StreamObserver<GetCardByIdResponse> observer) {
                        observer.onNext(GetCardByIdResponse.newBuilder().setCard(Card.newBuilder()
                                .setId("bad-id").build()).build());
                        observer.onCompleted();
                    }
                });
        assertThatThrownBy(() -> malformed.get(cardId))
                .isInstanceOf(BuyListExceptions.DependencyUnavailableException.class)
                .hasMessageContaining("malformed canonical ID");
    }

    private GrpcCardCatalogGateway gateway(CardCatalogServiceGrpc.CardCatalogServiceImplBase service)
            throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return new GrpcCardCatalogGateway(CardCatalogServiceGrpc.newBlockingStub(channel));
    }
}
