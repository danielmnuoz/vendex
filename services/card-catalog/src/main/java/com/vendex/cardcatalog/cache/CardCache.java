package com.vendex.cardcatalog.cache;

import com.vendex.cardcatalog.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-through cache for individual cards. SearchCards intentionally does
 * not use this — its result sets vary too much per query for caching to
 * win. Implementations MUST be best-effort: a backend outage logs and
 * returns "miss"/no-op, never throws into the request path.
 */
public interface CardCache {

    Optional<Card> get(String externalId);

    /** Returns the cached subset; missing keys are simply absent from the map. */
    Map<String, Card> getMany(List<String> externalIds);

    void put(Card card);

    void putMany(List<Card> cards);
}
