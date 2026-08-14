package com.vendex.inventory.catalog;

import java.util.List;
import java.util.UUID;

public interface CardCatalogGateway {
    CanonicalCard get(UUID cardId);
    List<CanonicalCard> search(String cardName, int limit);

    record CanonicalCard(
            UUID id,
            String externalId,
            String name,
            String setId,
            String setName
    ) {}
}
