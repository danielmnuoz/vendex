package com.vendex.cardcatalog.service;

import com.vendex.cardcatalog.cache.CardCache;
import com.vendex.cardcatalog.config.CardCatalogProperties;
import com.vendex.cardcatalog.domain.Card;
import com.vendex.cardcatalog.domain.SetSummary;
import com.vendex.cardcatalog.repository.CardRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side façade for the card catalog. Stateless; safe to call
 * concurrently. The gRPC adapter is intentionally thin so a future REST
 * surface (admin tooling) can reuse the same service.
 *
 * <p>Cache strategy: cache-aside per {@code external_id}. {@link
 * #searchCards} skips the cache entirely — result sets vary too much per
 * query for caching to win.
 */
@Service
public class CardService {

    private final CardRepository cards;
    private final CardCache cache;
    private final CardCatalogProperties props;

    public CardService(CardRepository cards, CardCache cache, CardCatalogProperties props) {
        this.cards = cards;
        this.cache = cache;
        this.props = props;
    }

    public Card getById(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            throw new CardExceptions.ValidationException("card_id is required");
        }
        UUID canonicalId = parseUuid(cardId);
        if (canonicalId != null) {
            Card card = cards.findById(canonicalId)
                    .orElseThrow(() -> new CardExceptions.CardNotFoundException(cardId));
            cache.put(card);
            return card;
        }

        String externalId = cardId;
        Optional<Card> cached = cache.get(externalId);
        if (cached.isPresent()) {
            return cached.get();
        }
        Card card = cards.findByExternalId(externalId)
                .orElseThrow(() -> new CardExceptions.CardNotFoundException(externalId));
        cache.put(card);
        return card;
    }

    /**
     * Hydrate up to N cards by canonical UUID or legacy external ID. Unknown
     * IDs are simply absent from the response; callers can diff against the
     * request to detect missing ones. Order matches the order requested.
     */
    public List<Card> getByIds(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        List<UUID> canonicalIds = new ArrayList<>();
        List<String> externalIds = new ArrayList<>();
        for (String cardId : cardIds) {
            UUID canonicalId = parseUuid(cardId);
            if (canonicalId == null) {
                externalIds.add(cardId);
            } else {
                canonicalIds.add(canonicalId);
            }
        }

        // Copy: cache implementations may return immutable maps (e.g. Map.of() when
        // externalIds is empty, which is the common all-canonical-UUID case).
        Map<String, Card> hits = new LinkedHashMap<>(cache.getMany(externalIds));
        List<String> missing = new ArrayList<>();
        for (String id : externalIds) {
            if (!hits.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            List<Card> loaded = cards.findByExternalIds(missing);
            cache.putMany(loaded);
            for (Card c : loaded) {
                hits.put(c.externalId(), c);
            }
        }

        if (!canonicalIds.isEmpty()) {
            for (Card card : cards.findByIds(canonicalIds)) {
                hits.put(card.id().toString(), card);
            }
        }
        Map<String, Card> ordered = new LinkedHashMap<>();
        for (String id : cardIds) {
            Card c = hits.get(id);
            if (c != null) {
                ordered.put(id, c);
            }
        }
        return List.copyOf(ordered.values());
    }

    public SearchResult searchCards(String query, String setIdFilter, String rarityFilter,
                                    int pageSize, String pageToken) {
        int effectivePageSize = clampPageSize(pageSize);
        int offset = PageToken.decode(pageToken);

        // Fetch one extra to detect "more available" without a COUNT.
        List<Card> rows = cards.search(query, setIdFilter, rarityFilter, effectivePageSize + 1, offset);

        String nextToken = null;
        List<Card> page = rows;
        if (rows.size() > effectivePageSize) {
            page = rows.subList(0, effectivePageSize);
            nextToken = PageToken.encode(offset + effectivePageSize);
        }
        return new SearchResult(List.copyOf(page), nextToken);
    }

    public List<SetSummary> listSets() {
        return cards.listSets();
    }

    private int clampPageSize(int requested) {
        CardCatalogProperties.Search s = props.search();
        if (requested <= 0) {
            return s.defaultPageSize();
        }
        return Math.min(requested, s.maxPageSize());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record SearchResult(List<Card> cards, String nextPageToken) {}
}
