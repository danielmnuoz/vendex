package com.vendex.cardcatalog.service;

import com.vendex.cardcatalog.cache.CardCache;
import com.vendex.cardcatalog.domain.Card;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory CardCache used by service unit tests. Tracks how often get/put
 * was called so tests can assert cache-aside behavior without a real Redis.
 */
public class FakeCardCache implements CardCache {

    public final Map<String, Card> store = new HashMap<>();
    public int getCalls;
    public int getManyCalls;
    public int putCalls;
    public int putManyCalls;

    @Override
    public Optional<Card> get(String externalId) {
        getCalls++;
        return Optional.ofNullable(store.get(externalId));
    }

    @Override
    public Map<String, Card> getMany(List<String> externalIds) {
        getManyCalls++;
        Map<String, Card> out = new LinkedHashMap<>();
        for (String id : externalIds) {
            Card c = store.get(id);
            if (c != null) {
                out.put(id, c);
            }
        }
        return out;
    }

    @Override
    public void put(Card card) {
        putCalls++;
        store.put(card.externalId(), card);
    }

    @Override
    public void putMany(List<Card> cards) {
        putManyCalls++;
        for (Card c : cards) {
            store.put(c.externalId(), c);
        }
    }
}
