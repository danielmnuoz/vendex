package com.vendex.cardcatalog.repository;

import com.vendex.cardcatalog.domain.Card;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.domain.SetSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the SQL the repository depends on against a real Postgres:
 *
 * <ul>
 *   <li>UPSERT preserves UUID + non-null fields under thin re-seeds (COALESCE branch)</li>
 *   <li>pg_trgm fuzzy search ranks more-similar names first</li>
 *   <li>ListSets aggregation reports correct card counts per set</li>
 * </ul>
 */
@JdbcTest
@Testcontainers
@Import(CardRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
class CardRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CardRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void wipe() {
        jdbc.execute("DELETE FROM cards");
    }

    @Test
    void upsertPreservesUuidAndCoalescesNulls() {
        // First seed: full payload.
        CardSeed full = new CardSeed(
                "sv03-001", "Pikachu", "sv03", "Obsidian Flames",
                "Scarlet & Violet", "Rare",
                "https://x/low.png", "https://x/high.png",
                LocalDate.of(2023, 8, 11));
        Card first = repo.upsert(full);
        UUID originalId = first.id();

        // Re-seed with a thinner payload — nullable fields set to null.
        CardSeed thin = new CardSeed(
                "sv03-001", "Pikachu (Holo)", "sv03", "Obsidian Flames",
                null, null, null, null, null);
        Card second = repo.upsert(thin);

        assertThat(second.id()).isEqualTo(originalId);
        assertThat(second.name()).isEqualTo("Pikachu (Holo)");           // overwritten
        assertThat(second.setSeries()).isEqualTo("Scarlet & Violet");    // preserved
        assertThat(second.rarity()).isEqualTo("Rare");                   // preserved
        assertThat(second.imageUrl()).isEqualTo("https://x/low.png");    // preserved
        assertThat(second.releaseDate()).isEqualTo(LocalDate.of(2023, 8, 11));
    }

    @Test
    void searchOrdersByTrigramSimilarity() {
        repo.upsert(seedFor("a", "Pikachu"));
        repo.upsert(seedFor("b", "Pikachu VMAX"));
        repo.upsert(seedFor("c", "Charizard"));
        repo.upsert(seedFor("d", "Mewtwo"));

        List<Card> hits = repo.search("Pikachu", null, null, 10, 0);

        assertThat(hits).extracting(Card::externalId)
                .as("Pikachu first (exact match), then Pikachu VMAX; Charizard/Mewtwo dropped")
                .contains("a", "b")
                .doesNotContain("d");
        assertThat(hits.get(0).externalId()).isEqualTo("a");
    }

    @Test
    void searchHonorsSetFilter() {
        repo.upsert(seedForSet("a", "Pikachu", "sv03"));
        repo.upsert(seedForSet("b", "Pikachu", "sv04"));

        List<Card> hits = repo.search("Pikachu", "sv04", null, 10, 0);

        assertThat(hits).extracting(Card::externalId).containsExactly("b");
    }

    @Test
    void searchBlankReturnsAlphabeticalAcrossAllRows() {
        repo.upsert(seedFor("a", "Mewtwo"));
        repo.upsert(seedFor("b", "Charizard"));
        repo.upsert(seedFor("c", "Pikachu"));

        List<Card> hits = repo.search(null, null, null, 10, 0);

        assertThat(hits).extracting(Card::name)
                .containsExactly("Charizard", "Mewtwo", "Pikachu");
    }

    @Test
    void listSetsAggregatesCountsPerSet() {
        repo.upsert(seedForSet("a", "X", "sv03"));
        repo.upsert(seedForSet("b", "Y", "sv03"));
        repo.upsert(seedForSet("c", "Z", "sv04"));

        List<SetSummary> sets = repo.listSets();

        assertThat(sets).hasSize(2);
        assertThat(sets).extracting(SetSummary::id, SetSummary::cardCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("sv03", 2),
                        org.assertj.core.groups.Tuple.tuple("sv04", 1)
                );
    }

    @Test
    void findByExternalIdsReturnsKnownSubset() {
        repo.upsert(seedFor("a", "X"));
        repo.upsert(seedFor("b", "Y"));
        List<Card> got = repo.findByExternalIds(List.of("a", "missing", "b"));
        assertThat(got).extracting(Card::externalId).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void findByExternalIdReturnsEmptyForUnknown() {
        Optional<Card> got = repo.findByExternalId("missing");
        assertThat(got).isEmpty();
    }

    private static CardSeed seedFor(String externalId, String name) {
        return seedForSet(externalId, name, "sv03");
    }

    private static CardSeed seedForSet(String externalId, String name, String setId) {
        return new CardSeed(externalId, name, setId, "Some Set", null, null, null, null, null);
    }
}
