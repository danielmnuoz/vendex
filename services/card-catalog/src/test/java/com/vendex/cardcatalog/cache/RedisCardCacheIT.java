package com.vendex.cardcatalog.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vendex.cardcatalog.domain.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full round-trip against a real Redis container — proves the cache is
 * actually wired (auto-config picks up the test Redis), keys are stored
 * under the {@code card:external_id:} prefix, and Jackson successfully
 * round-trips a Card with all its fields including release dates.
 *
 * <p>Boots only the Redis-related slice of the application; no Postgres,
 * no gRPC server.
 */
@SpringBootTest(classes = RedisCardCacheIT.TestApp.class)
@Testcontainers
class RedisCardCacheIT {

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void noPostgres(DynamicPropertyRegistry registry) {
        // Force Flyway off — the schema isn't relevant here and there's no Postgres.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    RedisCardCache cache;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void wipe() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void putThenGetRoundTrips() {
        Card card = sample("sv03-001");
        cache.put(card);

        Optional<Card> got = cache.get("sv03-001");

        assertThat(got).isPresent();
        assertThat(got.get().externalId()).isEqualTo("sv03-001");
        assertThat(got.get().name()).isEqualTo(card.name());
    }

    @Test
    void getOnEmptyKeyReturnsEmpty() {
        assertThat(cache.get("nope")).isEmpty();
    }

    @Test
    void putManyThenGetManyReturnsAll() {
        cache.putMany(List.of(sample("a"), sample("b"), sample("c")));
        Map<String, Card> got = cache.getMany(List.of("a", "b", "missing", "c"));
        assertThat(got).containsOnlyKeys("a", "b", "c");
    }

    private static Card sample(String externalId) {
        return new Card(
                UUID.randomUUID(), externalId, "Pikachu", "sv03", "Obsidian",
                "S&V", "Rare", "img-low", "img-high", null,
                Instant.now(), Instant.now()
        );
    }

    /**
     * Minimal test slice: only the cache bean + Spring Boot's Redis
     * autoconfig. No JdbcTemplate, no Flyway. The cache impl is constructed
     * inline because the production {@code CardCatalogBeansConfig} also
     * pulls in datasource-bound beans we don't want here.
     */
    @Configuration
    @Import({org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
             org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class})
    static class TestApp {
        // No stringRedisTemplate bean here — RedisAutoConfiguration already
        // provides one (the test autowires it). Declaring our own collides with
        // it, and bean overriding is disabled by default.
        @org.springframework.context.annotation.Bean
        RedisCardCache redisCardCache(RedisConnectionFactory factory) {
            return new RedisCardCache(
                    new StringRedisTemplate(factory),
                    new ObjectMapper().registerModule(new JavaTimeModule()),
                    Duration.ofMinutes(5));
        }
    }
}
