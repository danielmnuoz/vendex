package com.vendex.cardcatalog.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.cardcatalog.domain.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis-backed cache. Every call is wrapped in try/catch: a Redis outage
 * degrades to "no cache" rather than failing the gRPC request.
 *
 * <p>Keys: {@code card:external_id:<id>}. Values: JSON-encoded {@link Card}.
 * TTL is configured per environment ({@code card-catalog.cache.ttl}).
 */
public class RedisCardCache implements CardCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCardCache.class);
    private static final String KEY_PREFIX = "card:external_id:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public RedisCardCache(StringRedisTemplate redis, ObjectMapper json, Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    @Override
    public Optional<Card> get(String externalId) {
        try {
            String value = redis.opsForValue().get(key(externalId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(json.readValue(value, Card.class));
        } catch (Exception e) {
            log.warn("redis get failed for external_id={}: {}", externalId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Card> getMany(List<String> externalIds) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> keys = externalIds.stream().map(RedisCardCache::key).toList();
            List<String> values = redis.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<String, Card> out = new LinkedHashMap<>();
            for (int i = 0; i < externalIds.size(); i++) {
                String v = values.get(i);
                if (v == null) {
                    continue;
                }
                try {
                    out.put(externalIds.get(i), json.readValue(v, Card.class));
                } catch (JsonProcessingException jpe) {
                    log.warn("redis getMany: stale/corrupt cache entry for {}", externalIds.get(i));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("redis multiGet failed: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public void put(Card card) {
        try {
            redis.opsForValue().set(key(card.externalId()), json.writeValueAsString(card), ttl);
        } catch (Exception e) {
            log.warn("redis put failed for external_id={}: {}", card.externalId(), e.getMessage());
        }
    }

    @Override
    public void putMany(List<Card> cards) {
        if (cards.isEmpty()) {
            return;
        }
        try {
            Map<String, String> payload = new HashMap<>();
            for (Card card : cards) {
                payload.put(key(card.externalId()), json.writeValueAsString(card));
            }
            redis.opsForValue().multiSet(payload);
            // multiSet doesn't take TTL; set individually. Best-effort.
            for (String k : payload.keySet()) {
                redis.expire(k, ttl);
            }
        } catch (Exception e) {
            log.warn("redis multiSet failed: {}", e.getMessage());
        }
    }

    private static String key(String externalId) {
        return KEY_PREFIX + externalId;
    }
}
