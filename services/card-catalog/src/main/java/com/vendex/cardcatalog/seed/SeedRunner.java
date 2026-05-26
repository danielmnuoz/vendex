package com.vendex.cardcatalog.seed;

import com.vendex.cardcatalog.config.CardCatalogProperties;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.repository.CardRepository;
import com.vendex.cardcatalog.tcgdex.TcgDexClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Walks TCGdex and upserts every card into the local catalog. Runs only
 * under the {@code seed} Spring profile so the regular service start-up
 * never touches the network:
 *
 * <pre>
 * java -jar card-catalog-exec.jar --spring.profiles.active=seed \
 *      [--card-catalog.seed.dry-run=true] [--card-catalog.seed.max-sets=2]
 * </pre>
 *
 * <p>After the run completes the JVM exits with {@link SpringApplication#exit}.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final TcgDexClient tcgdex;
    private final CardRepository cards;
    private final CardCatalogProperties props;
    private final ApplicationContext context;

    public SeedRunner(TcgDexClient tcgdex,
                      CardRepository cards,
                      CardCatalogProperties props,
                      ApplicationContext context) {
        this.tcgdex = tcgdex;
        this.cards = cards;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        int[] exitCodeRef = {0};
        try {
            CardCatalogProperties.Seed seed = props.seed();
            log.info("seed: starting (dryRun={}, maxSets={})", seed.dryRun(), seed.maxSets());

            Map<String, String> series = Map.of();
            if (seed.enrichSeries()) {
                log.info("seed: fetching series index");
                series = tcgdex.fetchSeriesIndex();
                log.info("seed: fetched {} series", series.size());
            }

            List<CardSeed> seeds = tcgdex.fetchAllSeeds(series, seed.maxSets());
            log.info("seed: fetched {} cards from TCGdex", seeds.size());

            if (seed.dryRun()) {
                log.info("seed: dry-run; skipping database writes");
            } else {
                long started = System.currentTimeMillis();
                cards.upsertAll(seeds);
                log.info("seed: upserted {} cards in {} ms",
                        seeds.size(), System.currentTimeMillis() - started);
            }
        } catch (Exception e) {
            log.error("seed: failed", e);
            exitCodeRef[0] = 1;
        } finally {
            // Shut the context down cleanly so the JVM exits with the right code.
            System.exit(SpringApplication.exit(context, () -> exitCodeRef[0]));
        }
    }
}
