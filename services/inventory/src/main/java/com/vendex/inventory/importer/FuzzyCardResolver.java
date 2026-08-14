package com.vendex.inventory.importer;

import com.vendex.inventory.catalog.CardCatalogGateway;
import com.vendex.inventory.config.InventoryProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class FuzzyCardResolver {

    private static final int SEARCH_LIMIT = 25;

    private final CardCatalogGateway cards;
    private final InventoryProperties properties;

    public FuzzyCardResolver(CardCatalogGateway cards, InventoryProperties properties) {
        this.cards = cards;
        this.properties = properties;
    }

    public Resolution resolve(String cardName, String setName) {
        List<Candidate> candidates = cards.search(cardName, SEARCH_LIMIT).stream()
                .map(card -> new Candidate(card, score(cardName, setName, card)))
                .sorted(Comparator.comparingDouble(Candidate::confidence).reversed())
                .limit(3)
                .toList();

        if (candidates.isEmpty()) {
            return new Resolution(Optional.empty(), List.of(), "no catalog candidates found");
        }

        Candidate best = candidates.getFirst();
        double threshold = properties.matching().autoMatchThreshold();
        if (best.confidence() < threshold) {
            return new Resolution(Optional.empty(), candidates,
                    "best candidate is below the auto-match confidence threshold");
        }

        if (candidates.size() > 1) {
            double gap = best.confidence() - candidates.get(1).confidence();
            boolean exact = best.confidence() >= 0.999_999;
            if (!exact && gap < properties.matching().ambiguityGap()) {
                return new Resolution(Optional.empty(), candidates,
                        "multiple catalog candidates are too close to choose safely");
            }
        }
        return new Resolution(Optional.of(best), candidates, null);
    }

    private static double score(String requestedName, String requestedSet,
                                CardCatalogGateway.CanonicalCard candidate) {
        double nameScore = similarity(normalize(requestedName), normalize(candidate.name()));
        double setScore = similarity(normalize(requestedSet), normalize(candidate.setName()));
        return round(0.72 * nameScore + 0.28 * setScore);
    }

    private static double similarity(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0 - (double) previous[right.length()] / Math.max(left.length(), right.length());
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record Candidate(CardCatalogGateway.CanonicalCard card, double confidence) {}
    public record Resolution(Optional<Candidate> match, List<Candidate> candidates, String reason) {}
}
