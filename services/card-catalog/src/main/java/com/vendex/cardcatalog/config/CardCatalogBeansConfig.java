package com.vendex.cardcatalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vendex.cardcatalog.cache.CardCache;
import com.vendex.cardcatalog.cache.NoopCardCache;
import com.vendex.cardcatalog.cache.RedisCardCache;
import com.vendex.cardcatalog.tcgdex.TcgDexClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

/**
 * Bean wiring that benefits from being explicit:
 *
 * <ul>
 *   <li>{@link CardCache} — Redis impl by default, no-op under {@code seed}
 *       so the seed CLI doesn't require a running Redis.</li>
 *   <li>{@link TcgDexClient} — built from a profile-pinned {@link RestClient}
 *       so unit tests can override base URL with MockWebServer.</li>
 *   <li>{@link ObjectMapper} for Redis JSON — registers the JSR-310 module so
 *       {@link java.time.Instant} round-trips correctly.</li>
 * </ul>
 */
@Configuration
public class CardCatalogBeansConfig {

    @Bean
    public ObjectMapper cacheObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * The Redis template is consumed via {@link ObjectProvider} so a missing
     * StringRedisTemplate (the {@code seed} profile excludes Redis autoconfig
     * — see {@code application-seed.yaml}) falls back cleanly to the no-op
     * cache instead of failing context startup.
     */
    @Bean
    public CardCache cardCache(ObjectProvider<StringRedisTemplate> redis,
                               ObjectMapper cacheObjectMapper,
                               CardCatalogProperties props) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null || !props.cache().enabled()) {
            return new NoopCardCache();
        }
        return new RedisCardCache(template, cacheObjectMapper, props.cache().ttl());
    }

    @Bean
    public TcgDexClient tcgDexClient(CardCatalogProperties props) {
        RestClient http = RestClient.builder()
                .baseUrl(props.tcgdex().baseUrl())
                .build();
        return new TcgDexClient(http);
    }
}
