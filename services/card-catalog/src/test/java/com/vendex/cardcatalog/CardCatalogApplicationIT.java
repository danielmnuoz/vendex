package com.vendex.cardcatalog;

import com.redis.testcontainers.RedisContainer;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.grpc.CardCatalogGrpcService;
import com.vendex.cardcatalog.repository.CardRepository;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.GetCardByIdResponse;
import com.vendex.cards.v1.ListSetsRequest;
import com.vendex.cards.v1.ListSetsResponse;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.cards.v1.SearchCardsResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke: boots the full Spring context (Postgres + Redis +
 * gRPC server registration) and exercises {@link CardCatalogGrpcService}
 * directly via {@link StreamObserver}. Equivalent to a real gRPC call,
 * without the port-discovery hoops — the auth IT already proves the gRPC
 * server itself can bind and serve requests.
 */
@SpringBootTest
@Testcontainers
class CardCatalogApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7-alpine");

    @Autowired
    CardCatalogGrpcService grpc;

    @Autowired
    CardRepository cards;

    @Test
    void searchCardsReturnsTrigramRankedResults() {
        cards.upsert(new CardSeed("sv03-001", "Pikachu", "sv03", "Obsidian", null, "Rare", null, null, null));
        cards.upsert(new CardSeed("sv03-002", "Pikachu VMAX", "sv03", "Obsidian", null, "Holo", null, null, null));

        SearchCardsResponse resp = invoke(obs -> grpc.searchCards(
                SearchCardsRequest.newBuilder().setQuery("Pikachu").setPageSize(10).build(), obs));

        assertThat(resp.getCardsList()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(resp.getCardsList().get(0).getName()).contains("Pikachu");
    }

    @Test
    void getCardByIdHotCacheRoundTrip() {
        cards.upsert(new CardSeed("sv03-100", "Charizard", "sv03", "Obsidian",
                null, "Ultra Rare", null, null, null));

        GetCardByIdResponse first = invoke(obs -> grpc.getCardById(
                GetCardByIdRequest.newBuilder().setCardId("sv03-100").build(), obs));
        GetCardByIdResponse second = invoke(obs -> grpc.getCardById(
                GetCardByIdRequest.newBuilder().setCardId("sv03-100").build(), obs));

        assertThat(first.getCard().getName()).isEqualTo("Charizard");
        assertThat(second.getCard()).isEqualTo(first.getCard());
    }

    @Test
    void getCardByIdMissingMapsToNotFound() {
        StatusRuntimeException err = CardCatalogApplicationIT.<GetCardByIdResponse>invokeError(
                obs -> grpc.getCardById(GetCardByIdRequest.newBuilder().setCardId("does-not-exist").build(), obs));
        assertThat(err.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    void listSetsAggregatesByCount() {
        cards.upsert(new CardSeed("a", "X", "sv03", "Obsidian Flames", null, null, null, null, null));
        cards.upsert(new CardSeed("b", "Y", "sv03", "Obsidian Flames", null, null, null, null, null));
        cards.upsert(new CardSeed("c", "Z", "sv04", "Paradox Rift", null, null, null, null, null));

        ListSetsResponse resp = invoke(obs -> grpc.listSets(ListSetsRequest.newBuilder().build(), obs));

        assertThat(resp.getSetsList())
                .extracting(s -> s.getId() + ":" + s.getCardCount())
                .contains("sv03:2", "sv04:1");
    }

    // --- StreamObserver helpers ---

    private static <T> T invoke(java.util.function.Consumer<StreamObserver<T>> call) {
        List<T> out = new ArrayList<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        call.accept(new StreamObserver<>() {
            @Override public void onNext(T value) { out.add(value); }
            @Override public void onError(Throwable t) { err.set(t); }
            @Override public void onCompleted() {}
        });
        if (err.get() != null) {
            throw new AssertionError("unexpected gRPC error", err.get());
        }
        return out.get(0);
    }

    private static <T> StatusRuntimeException invokeError(java.util.function.Consumer<StreamObserver<T>> call) {
        AtomicReference<Throwable> err = new AtomicReference<>();
        call.accept(new StreamObserver<>() {
            @Override public void onNext(T value) {}
            @Override public void onError(Throwable t) { err.set(t); }
            @Override public void onCompleted() {}
        });
        Throwable t = err.get();
        if (t == null) {
            throw new AssertionError("expected gRPC error but none was raised");
        }
        if (!(t instanceof StatusRuntimeException sre)) {
            throw new AssertionError("expected StatusRuntimeException, got " + t.getClass(), t);
        }
        return sre;
    }
}
