package com.vendex.inventory.importer;

import com.vendex.inventory.catalog.CardCatalogGateway;
import com.vendex.inventory.config.InventoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FuzzyCardResolverTest {

    private CardCatalogGateway cards;
    private FuzzyCardResolver resolver;

    @BeforeEach
    void setUp() {
        cards = mock(CardCatalogGateway.class);
        InventoryProperties properties = new InventoryProperties("unused",
                new InventoryProperties.Csv(1000, 1_000_000),
                new InventoryProperties.Matching(0.82, 0.05));
        resolver = new FuzzyCardResolver(cards, properties);
    }

    @Test
    void exactNameAndSetAutoMatch() {
        var exact = card("Pikachu VMAX", "Vivid Voltage");
        var other = card("Pikachu V", "Vivid Voltage");
        when(cards.search("Pikachu VMAX", 25)).thenReturn(List.of(other, exact));

        FuzzyCardResolver.Resolution result = resolver.resolve("Pikachu VMAX", "Vivid Voltage");

        assertThat(result.match()).isPresent();
        assertThat(result.match().orElseThrow().card()).isEqualTo(exact);
        assertThat(result.match().orElseThrow().confidence()).isEqualTo(1.0);
    }

    @Test
    void typoCanAutoMatchWhenCandidateIsClear() {
        var exact = card("Charizard ex", "Obsidian Flames");
        var other = card("Charizard", "Base Set");
        when(cards.search("Charzard ex", 25)).thenReturn(List.of(exact, other));

        FuzzyCardResolver.Resolution result = resolver.resolve("Charzard ex", "Obsidian Flame");

        assertThat(result.match()).isPresent();
        assertThat(result.match().orElseThrow().card()).isEqualTo(exact);
    }

    @Test
    void closeCandidatesRequireManualReview() {
        var first = card("Pikachu", "Base Set");
        var second = card("Pikachu", "Base Set 2");
        when(cards.search("Pikachu", 25)).thenReturn(List.of(first, second));

        FuzzyCardResolver.Resolution result = resolver.resolve("Pikachu", "Base Set X");

        assertThat(result.match()).isEmpty();
        assertThat(result.reason()).contains("too close");
        assertThat(result.candidates()).hasSize(2);
    }

    @Test
    void noCandidatesProducesManualIssue() {
        when(cards.search("Missing", 25)).thenReturn(List.of());

        FuzzyCardResolver.Resolution result = resolver.resolve("Missing", "Unknown");

        assertThat(result.match()).isEmpty();
        assertThat(result.reason()).contains("no catalog candidates");
    }

    private static CardCatalogGateway.CanonicalCard card(String name, String setName) {
        return new CardCatalogGateway.CanonicalCard(
                UUID.randomUUID(), "external", name, "set-id", setName);
    }
}
