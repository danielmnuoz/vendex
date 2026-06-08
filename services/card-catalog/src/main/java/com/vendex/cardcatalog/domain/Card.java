package com.vendex.cardcatalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Single canonical card row mirrored from TCGdex.
 *
 * <p>Nullable fields ({@code setSeries}, {@code rarity}, {@code imageUrl},
 * {@code imageUrlLarge}, {@code releaseDate}) reflect TCGdex's inconsistent
 * publishing. The UPSERT in {@link com.vendex.cardcatalog.repository.CardRepository}
 * uses {@code COALESCE} so a thin re-seed never wipes richer existing values.
 */
@Table("cards")
public record Card(
        @Id UUID id,
        String externalId,
        String name,
        String setId,
        String setName,
        String setSeries,
        String rarity,
        String imageUrl,
        String imageUrlLarge,
        LocalDate releaseDate,
        Instant createdAt,
        Instant updatedAt
) {}
