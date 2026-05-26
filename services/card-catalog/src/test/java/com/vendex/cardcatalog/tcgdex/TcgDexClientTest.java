package com.vendex.cardcatalog.tcgdex;

import com.vendex.cardcatalog.domain.CardSeed;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TcgDexClientTest {

    private MockWebServer server;
    private TcgDexClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        RestClient http = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new TcgDexClient(http);
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void listSetsParsesArray() {
        server.enqueue(jsonOk("""
                [
                  {"id": "sv03", "name": "Obsidian Flames", "cardCount": 230},
                  {"id": "sv04", "name": "Paradox Rift",    "cardCount": 266}
                ]
                """));

        List<TcgDexClient.SetStub> sets = client.listSets();

        assertThat(sets).extracting(TcgDexClient.SetStub::id).containsExactly("sv03", "sv04");
    }

    @Test
    void fetchAllSeedsFlattensCardsAndDerivesImages() {
        server.enqueue(jsonOk("""
                [{"id": "sv03", "name": "Obsidian Flames"}]
                """));
        server.enqueue(jsonOk("""
                {
                  "id": "sv03",
                  "name": "Obsidian Flames",
                  "releaseDate": "2023-08-11",
                  "serie": {"id": "sv", "name": "Scarlet & Violet"},
                  "cards": [
                    {"id": "sv03-001", "name": "Pikachu", "rarity": "Rare",
                     "image": "https://assets.tcgdex.net/en/sv/sv03/001"}
                  ]
                }
                """));

        List<CardSeed> seeds = client.fetchAllSeeds(Map.of("sv", "Scarlet & Violet"), 0);

        assertThat(seeds).hasSize(1);
        CardSeed seed = seeds.get(0);
        assertThat(seed.externalId()).isEqualTo("sv03-001");
        assertThat(seed.setSeries()).isEqualTo("Scarlet & Violet");
        assertThat(seed.imageUrl()).endsWith("/low.png");
        assertThat(seed.imageUrlLarge()).endsWith("/high.png");
        assertThat(seed.releaseDate()).hasToString("2023-08-11");
    }

    @Test
    void maxSetsClampsWalk() {
        server.enqueue(jsonOk("""
                [
                  {"id": "sv01", "name": "A"},
                  {"id": "sv02", "name": "B"},
                  {"id": "sv03", "name": "C"}
                ]
                """));
        // Only one /sets/:id call should be made because maxSets=1
        server.enqueue(jsonOk("""
                {"id": "sv01", "name": "A", "cards": []}
                """));

        List<CardSeed> seeds = client.fetchAllSeeds(null, 1);

        assertThat(seeds).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void brokenSetIsSkippedNotFatal() {
        server.enqueue(jsonOk("""
                [{"id": "good", "name": "OK"}, {"id": "bad", "name": "Bad"}]
                """));
        // First set succeeds.
        server.enqueue(jsonOk("""
                {"id": "good", "name": "OK", "cards": [
                    {"id": "good-1", "name": "X", "image": "https://x/img"}
                ]}
                """));
        // Second set returns 500 — should be logged and skipped.
        server.enqueue(new MockResponse().setResponseCode(500));

        List<CardSeed> seeds = client.fetchAllSeeds(null, 0);

        assertThat(seeds).extracting(CardSeed::externalId).containsExactly("good-1");
    }

    private static MockResponse jsonOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
