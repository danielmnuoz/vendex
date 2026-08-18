package com.vendex.buylist.catalog;

import java.util.UUID;

public interface CardCatalogGateway {
    CanonicalCard get(UUID cardId);

    record CanonicalCard(UUID id, String externalId, String name, String setId, String setName) {}
}
