package com.vendex.cardcatalog.cache;

import com.vendex.cardcatalog.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Used in the {@code seed} profile and in tests where the cache is
 * irrelevant. Keeps the {@code CardService} wiring uniform without a null
 * check on every read path.
 */
public class NoopCardCache implements CardCache {

    @Override
    public Optional<Card> get(String externalId) {
        return Optional.empty();
    }

    @Override
    public Map<String, Card> getMany(List<String> externalIds) {
        return Map.of();
    }

    @Override
    public void put(Card card) {}

    @Override
    public void putMany(List<Card> cards) {}
}
