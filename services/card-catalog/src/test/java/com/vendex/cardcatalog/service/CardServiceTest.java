package com.vendex.cardcatalog.service;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import com.vendex.cardcatalog.domain.Card;
import com.vendex.cardcatalog.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardServiceTest {

    private CardRepository repo;
    private FakeCardCache cache;
    private CardService service;

    @BeforeEach
    void setUp() {
        repo = mock(CardRepository.class);
        cache = new FakeCardCache();
        CardCatalogProperties props = new CardCatalogProperties(
                new CardCatalogProperties.Tcgdex("http://unused"),
                new CardCatalogProperties.Cache(true, Duration.ofMinutes(5)),
                new CardCatalogProperties.Search(25, 100),
                new CardCatalogProperties.Seed(0, false, true)
        );
        service = new CardService(repo, cache, props);
    }

    private static Card sample(String externalId) {
        return new Card(
                UUID.randomUUID(), externalId, "Pikachu", "sv03", "Obsidian Flames",
                "Scarlet & Violet", "Rare", "img-low", "img-high", null,
                Instant.now(), Instant.now()
        );
    }

    @Nested
    class GetById {
        @Test
        void cacheHitSkipsRepo() {
            Card card = sample("sv03-001");
            cache.store.put("sv03-001", card);

            Card got = service.getById("sv03-001");

            assertThat(got).isEqualTo(card);
            verify(repo, never()).findByExternalId(anyString());
        }

        @Test
        void cacheMissLoadsAndPopulates() {
            Card card = sample("sv03-002");
            when(repo.findByExternalId("sv03-002")).thenReturn(Optional.of(card));

            Card got = service.getById("sv03-002");

            assertThat(got).isEqualTo(card);
            assertThat(cache.putCalls).isEqualTo(1);
            assertThat(cache.store).containsKey("sv03-002");
        }

        @Test
        void notFoundThrowsCardNotFound() {
            when(repo.findByExternalId("missing")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById("missing"))
                    .isInstanceOf(CardExceptions.CardNotFoundException.class);
        }

        @Test
        void blankIdRejected() {
            assertThatThrownBy(() -> service.getById(" "))
                    .isInstanceOf(CardExceptions.ValidationException.class);
        }
    }

    @Nested
    class GetByIds {
        @Test
        void onlyMissingIdsHitRepo() {
            Card cached = sample("sv03-001");
            Card loaded = sample("sv03-002");
            cache.store.put("sv03-001", cached);
            when(repo.findByExternalIds(List.of("sv03-002"))).thenReturn(List.of(loaded));

            List<Card> got = service.getByIds(List.of("sv03-001", "sv03-002"));

            assertThat(got).extracting(Card::externalId)
                    .containsExactly("sv03-001", "sv03-002");
            assertThat(cache.putManyCalls).isEqualTo(1);
            assertThat(cache.store).containsKey("sv03-002");
        }

        @Test
        void missingIdsAreSilentlyDroppedFromResponse() {
            when(repo.findByExternalIds(List.of("nope"))).thenReturn(List.of());
            List<Card> got = service.getByIds(List.of("nope"));
            assertThat(got).isEmpty();
        }

        @Test
        void preservesRequestOrder() {
            Card a = sample("a");
            Card b = sample("b");
            cache.store.put("b", b);
            when(repo.findByExternalIds(List.of("a"))).thenReturn(List.of(a));
            List<Card> got = service.getByIds(List.of("b", "a"));
            assertThat(got).extracting(Card::externalId).containsExactly("b", "a");
        }

        @Test
        void emptyInputReturnsEmpty() {
            assertThat(service.getByIds(List.of())).isEmpty();
        }
    }

    @Nested
    class SearchCards {
        @Test
        void clampsToDefaultWhenPageSizeUnset() {
            when(repo.search(eq("pika"), any(), any(), eq(26), eq(0))).thenReturn(List.of(sample("a")));
            service.searchCards("pika", null, null, 0, null);
            verify(repo).search("pika", null, null, 26, 0);
        }

        @Test
        void clampsToMaxWhenOverLimit() {
            when(repo.search(any(), any(), any(), eq(101), anyInt())).thenReturn(List.of());
            service.searchCards("q", null, null, 5000, null);
            verify(repo).search("q", null, null, 101, 0);
        }

        @Test
        void emitsNextPageTokenWhenMoreAvailable() {
            // request page size 2; repo returns 3 to signal "more available"
            when(repo.search(any(), any(), any(), eq(3), eq(0)))
                    .thenReturn(List.of(sample("a"), sample("b"), sample("c")));
            CardService.SearchResult result = service.searchCards("q", null, null, 2, null);
            assertThat(result.cards()).hasSize(2);
            assertThat(result.nextPageToken()).isNotNull();
        }

        @Test
        void noNextTokenOnLastPage() {
            when(repo.search(any(), any(), any(), eq(3), eq(0)))
                    .thenReturn(List.of(sample("a"), sample("b")));
            CardService.SearchResult result = service.searchCards("q", null, null, 2, null);
            assertThat(result.cards()).hasSize(2);
            assertThat(result.nextPageToken()).isNull();
        }

        @Test
        void decodesPageTokenForOffset() {
            String token = PageToken.encode(25);
            when(repo.search(any(), any(), any(), anyInt(), eq(25))).thenReturn(List.of());
            service.searchCards("q", null, null, 25, token);
            verify(repo).search("q", null, null, 26, 25);
        }

        @Test
        void malformedTokenRejected() {
            assertThatThrownBy(() -> service.searchCards("q", null, null, 25, "!!!not-base64!!!"))
                    .isInstanceOf(CardExceptions.ValidationException.class);
        }
    }
}
