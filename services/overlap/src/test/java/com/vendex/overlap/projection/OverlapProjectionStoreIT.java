package com.vendex.overlap.projection;

import com.redis.testcontainers.RedisContainer;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.InventoryUpdated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OverlapProjectionStoreIT.TestApp.class)
@Testcontainers
class OverlapProjectionStoreIT {

    private static final Instant T1 = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7-alpine");

    @Autowired OverlapProjectionStore store;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void duplicateInventoryRowsKeepCardPresentUntilLastCopyIsRemoved() {
        UUID eventId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        UUID wantedId = UUID.randomUUID();

        store.applyInventory(inventory(firstItem, sellerId, null, cardId, "80.00",
                Action.ADDED, T1));
        store.applyInventory(inventory(secondItem, sellerId, null, cardId, "90.00",
                Action.ADDED, T1));
        store.applyDemand(demand(wantedId, buyerId, cardId, Action.ADDED, T1));
        store.applyRoster(eventId, sellerId, true, T1);
        store.applyRoster(eventId, buyerId, true, T1);

        assertThat(store.intersectCards(eventId, sellerId, buyerId)).containsExactly(cardId);
        assertThat(store.inventoryForCard(eventId, sellerId, cardId)).hasSize(2);

        assertThat(store.applyInventory(inventory(firstItem, sellerId, null, cardId, "80.00",
                Action.REMOVED, T1.plusSeconds(1)))).isEqualTo(ApplyResult.APPLIED);
        assertThat(store.intersectCards(eventId, sellerId, buyerId)).containsExactly(cardId);
        assertThat(store.inventoryForCard(eventId, sellerId, cardId))
                .extracting(snapshot -> snapshot.itemId()).containsExactly(secondItem);

        store.applyInventory(inventory(secondItem, sellerId, null, cardId, "90.00",
                Action.REMOVED, T1.plusSeconds(2)));
        assertThat(store.intersectCards(eventId, sellerId, buyerId)).isEmpty();
    }

    @Test
    void eventScopeIsIsolatedAndRemovalRejectsOlderOrEqualResurrection() {
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        store.applyDemand(demand(UUID.randomUUID(), buyerId, cardId, Action.ADDED, T1));
        store.applyRoster(firstEvent, sellerId, true, T1);
        store.applyRoster(firstEvent, buyerId, true, T1);
        store.applyRoster(secondEvent, sellerId, true, T1);
        store.applyRoster(secondEvent, buyerId, true, T1);
        store.applyInventory(inventory(itemId, sellerId, firstEvent, cardId, "10.00",
                Action.ADDED, T1));

        assertThat(store.intersectCards(firstEvent, sellerId, buyerId)).contains(cardId);
        assertThat(store.intersectCards(secondEvent, sellerId, buyerId)).isEmpty();

        Instant removedAt = T1.plusSeconds(10);
        store.applyInventory(inventory(itemId, sellerId, firstEvent, cardId, "10.00",
                Action.REMOVED, removedAt));
        assertThat(store.applyInventory(inventory(itemId, sellerId, firstEvent, cardId, "10.00",
                Action.ADDED, T1.plusSeconds(5)))).isEqualTo(ApplyResult.STALE);
        assertThat(store.applyInventory(inventory(itemId, sellerId, firstEvent, cardId, "10.00",
                Action.ADDED, removedAt))).isEqualTo(ApplyResult.STALE);
        assertThat(store.intersectCards(firstEvent, sellerId, buyerId)).isEmpty();
    }

    @Test
    void duplicateRosterDeliveryRematerializesExistingGlobalFacts() {
        UUID eventId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        store.applyInventory(inventory(UUID.randomUUID(), sellerId, null, cardId, "10.00",
                Action.ADDED, T1));
        store.applyDemand(demand(UUID.randomUUID(), buyerId, cardId, Action.ADDED, T1));
        assertThat(store.applyRoster(eventId, sellerId, true, T1))
                .isEqualTo(ApplyResult.APPLIED);
        store.applyRoster(eventId, buyerId, true, T1);

        assertThat(store.applyRoster(eventId, sellerId, true, T1))
                .isEqualTo(ApplyResult.DUPLICATE);
        assertThat(store.intersectCards(eventId, sellerId, buyerId)).contains(cardId);
    }

    private static InventoryUpdated inventory(
            UUID itemId, UUID vendorId, UUID eventId, UUID cardId, String price,
            Action action, Instant timestamp) {
        return new InventoryUpdated(itemId, vendorId, eventId, cardId, "NM", 2,
                new BigDecimal(price), "normal", action, timestamp);
    }

    private static BuyListUpdated demand(
            UUID wantedId, UUID vendorId, UUID cardId, Action action, Instant timestamp) {
        return new BuyListUpdated(wantedId, vendorId, cardId, "LP",
                new BigDecimal("100.00"), 1, action, timestamp);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({RedisAutoConfiguration.class, OverlapProjectionStore.class})
    static class TestApp {}
}
