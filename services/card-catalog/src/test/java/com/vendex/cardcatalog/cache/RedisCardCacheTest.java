package com.vendex.cardcatalog.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vendex.cardcatalog.domain.Card;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the cache degrades gracefully on Redis errors. The "happy path"
 * is exercised end-to-end by the real-Redis integration test
 * ({@code RedisCardCacheIT}) — here we only assert that exceptions are
 * swallowed.
 */
class RedisCardCacheTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void redisFailureOnGetReturnsEmpty() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));

        RedisCardCache cache = new RedisCardCache(redis, JSON, Duration.ofMinutes(5));

        Optional<Card> got = cache.get("anything");

        assertThat(got).isEmpty();
    }

    @Test
    void redisFailureOnPutIsSwallowed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        RedisCardCache cache = new RedisCardCache(redis, JSON, Duration.ofMinutes(5));

        Card card = new Card(
                UUID.randomUUID(), "ext-1", "Pikachu", "sv03", "Obsidian",
                null, null, null, null, null, Instant.now(), Instant.now()
        );

        // Should not throw — the cache is best-effort and the gRPC request must succeed.
        cache.put(card);
    }
}
