package com.vendex.cardcatalog.domain;

import java.time.LocalDate;

/**
 * Carrier for the subset of TCGdex fields the seed pipeline produces
 * before they're upserted into the {@code cards} table. Decoupled from
 * {@link Card} so we never need to invent IDs or timestamps client-side
 * — Postgres generates {@code id} and stamps {@code created_at} /
 * {@code updated_at} on insert.
 */
public record CardSeed(
        String externalId,
        String name,
        String setId,
        String setName,
        String setSeries,
        String rarity,
        String imageUrl,
        String imageUrlLarge,
        LocalDate releaseDate
) {}
