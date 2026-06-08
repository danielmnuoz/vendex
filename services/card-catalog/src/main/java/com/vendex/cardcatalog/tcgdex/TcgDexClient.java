package com.vendex.cardcatalog.tcgdex;

import com.vendex.cardcatalog.domain.CardSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thin wrapper over the TCGdex public REST API (https://api.tcgdex.net/v2/en).
 * Used only by the seed pipeline — never on the request path.
 *
 * <p>The API is documented at https://tcgdex.dev/. We hit:
 * <ul>
 *   <li>{@code GET /sets} — slim set list (id, name, cardCount)</li>
 *   <li>{@code GET /sets/{id}} — full set including embedded {@code cards[]}</li>
 *   <li>{@code GET /series} — slim series list (id, name)</li>
 *   <li>{@code GET /series/{id}} — series detail used to derive {@code set_series}</li>
 * </ul>
 *
 * <p>TCGdex publishes image URLs as a base path; the public convention is
 * {@code <base>/high.png} and {@code <base>/low.png}.
 */
public class TcgDexClient {

    private static final Logger log = LoggerFactory.getLogger(TcgDexClient.class);

    private final RestClient http;

    public TcgDexClient(RestClient http) {
        this.http = http;
    }

    public List<SetStub> listSets() {
        SetStub[] body = http.get().uri("/sets").retrieve().body(SetStub[].class);
        return body == null ? List.of() : List.of(body);
    }

    public SetDetail getSet(String setId) {
        return http.get().uri("/sets/{id}", setId).retrieve().body(SetDetail.class);
    }

    public List<SeriesStub> listSeries() {
        SeriesStub[] body = http.get().uri("/series").retrieve().body(SeriesStub[].class);
        return body == null ? List.of() : List.of(body);
    }

    public SeriesDetail getSeries(String seriesId) {
        return http.get().uri("/series/{id}", seriesId).retrieve().body(SeriesDetail.class);
    }

    /**
     * Walks every set and produces flat {@link CardSeed} rows ready for
     * upsert. Optional {@code seriesNameById} fills the {@code set_series}
     * column when callers have already enriched series metadata; passing
     * {@code null} leaves the field empty (UPSERT's COALESCE will preserve
     * any previously-known value).
     *
     * @param maxSets if positive, walk at most this many sets — used by
     *                {@code --seed.max-sets} smoke tests
     */
    public List<CardSeed> fetchAllSeeds(Map<String, String> seriesNameById, int maxSets) {
        List<SetStub> sets = listSets();
        if (maxSets > 0 && sets.size() > maxSets) {
            sets = sets.subList(0, maxSets);
        }
        List<CardSeed> out = new ArrayList<>();
        for (SetStub stub : sets) {
            try {
                SetDetail set = getSet(stub.id());
                if (set == null || set.cards() == null) {
                    continue;
                }
                String seriesName = seriesNameById == null ? null
                        : (set.serie() == null ? null : seriesNameById.get(set.serie().id()));
                for (CardStub card : set.cards()) {
                    out.add(toSeed(card, set, seriesName));
                }
            } catch (Exception e) {
                log.warn("skipping set {} (fetch failed): {}", stub.id(), e.getMessage());
            }
        }
        return out;
    }

    public Map<String, String> fetchSeriesIndex() {
        Map<String, String> out = new HashMap<>();
        for (SeriesStub s : listSeries()) {
            if (s.id() != null && s.name() != null) {
                out.put(s.id(), s.name());
            }
        }
        return out;
    }

    private static CardSeed toSeed(CardStub card, SetDetail set, String seriesName) {
        String base = card.image();
        String low = base == null ? null : base + "/low.png";
        String high = base == null ? null : base + "/high.png";
        return new CardSeed(
                card.id(),
                Objects.requireNonNullElse(card.name(), ""),
                set.id(),
                Objects.requireNonNullElse(set.name(), ""),
                seriesName,
                card.rarity(),
                low,
                high,
                parseDate(set.releaseDate())
        );
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    // --- DTOs. Public so Jackson can construct them via canonical record constructors. ---

    public record SetStub(String id, String name, Integer cardCount) {}

    public record SetDetail(
            String id,
            String name,
            String releaseDate,
            SeriesRef serie,
            List<CardStub> cards
    ) {}

    public record CardStub(
            String id,
            String name,
            String rarity,
            String image
    ) {}

    public record SeriesRef(String id, String name) {}

    public record SeriesStub(String id, String name) {}

    public record SeriesDetail(String id, String name) {}
}
