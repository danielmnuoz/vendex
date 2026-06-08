package com.vendex.cardcatalog.domain;

/**
 * Aggregated view of a TCGdex set: how many cards we know about, plus the
 * (optional) human-readable name and series. Produced by {@link
 * com.vendex.cardcatalog.repository.CardRepository#listSets()}.
 */
public record SetSummary(
        String id,
        String name,
        String series,
        int cardCount
) {}
