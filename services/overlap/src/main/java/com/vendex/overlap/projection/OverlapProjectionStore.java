package com.vendex.overlap.projection;

import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.InventoryUpdated;
import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.InventorySnapshot;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Replayable Redis read model. Entity hashes retain timestamped tombstones;
 * source indexes retain active item IDs; event sets are disposable materialized
 * views used by SINTER. Duplicate delivery repairs partially-applied indexes.
 */
@Repository
public class OverlapProjectionStore {

    private static final String PREFIX = "vendex:overlap:";

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashes;

    public OverlapProjectionStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.hashes = redis.opsForHash();
    }

    public synchronized ApplyResult applyInventory(InventoryUpdated fact) {
        validateInventory(fact);
        String scope = scope(fact.eventId());
        String snapshotKey = inventorySnapshotKey(scope, fact.inventoryItemId());
        Map<String, String> previous = hashes.entries(snapshotKey);
        ApplyResult result = compare(previous, fact.timestamp(), fact.action(), inventoryFields(fact));
        if (result == ApplyResult.STALE) {
            return result;
        }
        assertImmutable(previous, "vendor_id", fact.vendorId().toString(), "inventory vendor");
        assertImmutable(previous, "card_id", fact.cardId().toString(), "inventory card");

        boolean active = fact.action() != Action.REMOVED;
        Map<String, String> values = inventoryFields(fact);
        values.put("active", Boolean.toString(active));
        values.put("occurred_at", fact.timestamp().toString());
        values.put("action", fact.action().name());
        hashes.putAll(snapshotKey, values);

        String itemId = fact.inventoryItemId().toString();
        String cardId = fact.cardId().toString();
        String cardItemsKey = inventorySourceCardItemsKey(fact.eventId(), fact.vendorId(), fact.cardId());
        String sourceCardsKey = inventorySourceCardsKey(fact.eventId(), fact.vendorId());
        if (active) {
            redis.opsForSet().add(cardItemsKey, itemId);
            redis.opsForSet().add(sourceCardsKey, cardId);
        } else {
            redis.opsForSet().remove(cardItemsKey, itemId);
            removeCardWhenEmpty(cardItemsKey, sourceCardsKey, cardId);
        }

        if (fact.eventId() == null) {
            for (UUID eventId : eventsForVendor(fact.vendorId())) {
                refreshInventoryCard(eventId, fact.vendorId(), fact.cardId());
            }
        } else if (isVendorActive(fact.eventId(), fact.vendorId())) {
            refreshInventoryCard(fact.eventId(), fact.vendorId(), fact.cardId());
        }
        return result;
    }

    public synchronized ApplyResult applyDemand(BuyListUpdated fact) {
        validateDemand(fact);
        String snapshotKey = demandSnapshotKey(fact.wantedCardId());
        Map<String, String> previous = hashes.entries(snapshotKey);
        ApplyResult result = compare(previous, fact.timestamp(), fact.action(), demandFields(fact));
        if (result == ApplyResult.STALE) {
            return result;
        }
        assertImmutable(previous, "vendor_id", fact.vendorId().toString(), "wanted-card vendor");
        assertImmutable(previous, "card_id", fact.cardId().toString(), "wanted-card card");

        boolean active = fact.action() != Action.REMOVED;
        Map<String, String> values = demandFields(fact);
        values.put("active", Boolean.toString(active));
        values.put("occurred_at", fact.timestamp().toString());
        values.put("action", fact.action().name());
        hashes.putAll(snapshotKey, values);

        String wantedCardId = fact.wantedCardId().toString();
        String cardId = fact.cardId().toString();
        String cardItemsKey = demandSourceCardItemsKey(fact.vendorId(), fact.cardId());
        String sourceCardsKey = demandSourceCardsKey(fact.vendorId());
        if (active) {
            redis.opsForSet().add(cardItemsKey, wantedCardId);
            redis.opsForSet().add(sourceCardsKey, cardId);
        } else {
            redis.opsForSet().remove(cardItemsKey, wantedCardId);
            removeCardWhenEmpty(cardItemsKey, sourceCardsKey, cardId);
        }
        for (UUID eventId : eventsForVendor(fact.vendorId())) {
            refreshDemandCard(eventId, fact.vendorId(), fact.cardId());
        }
        return result;
    }

    public synchronized ApplyResult applyRoster(
            UUID eventId, UUID vendorId, boolean active, Instant occurredAt) {
        Objects.requireNonNull(eventId, "event_id is required");
        Objects.requireNonNull(vendorId, "vendor_id is required");
        Objects.requireNonNull(occurredAt, "timestamp is required");
        Action action = active ? Action.ADDED : Action.REMOVED;
        String key = rosterSnapshotKey(eventId, vendorId);
        Map<String, String> previous = hashes.entries(key);
        Map<String, String> incoming = Map.of(
                "event_id", eventId.toString(),
                "vendor_id", vendorId.toString(),
                "active", Boolean.toString(active));
        ApplyResult result = compare(previous, occurredAt, action, incoming);
        if (result == ApplyResult.STALE) {
            return result;
        }
        Map<String, String> values = new HashMap<>(incoming);
        values.put("occurred_at", occurredAt.toString());
        values.put("action", action.name());
        hashes.putAll(key, values);

        if (active) {
            redis.opsForSet().add(eventVendorsKey(eventId), vendorId.toString());
            redis.opsForSet().add(vendorEventsKey(vendorId), eventId.toString());
            materializeVendor(eventId, vendorId);
        } else {
            redis.opsForSet().remove(eventVendorsKey(eventId), vendorId.toString());
            redis.opsForSet().remove(vendorEventsKey(vendorId), eventId.toString());
            redis.delete(List.of(eventInventoryCardsKey(eventId, vendorId),
                    eventDemandCardsKey(eventId, vendorId)));
        }
        return result;
    }

    public boolean isVendorActive(UUID eventId, UUID vendorId) {
        return Boolean.TRUE.equals(redis.opsForSet()
                .isMember(eventVendorsKey(eventId), vendorId.toString()));
    }

    public Set<UUID> vendorsAtEvent(UUID eventId) {
        return uuidSet(members(eventVendorsKey(eventId)));
    }

    public Set<UUID> eventsForVendor(UUID vendorId) {
        return uuidSet(members(vendorEventsKey(vendorId)));
    }

    public Set<UUID> intersectCards(UUID eventId, UUID sellerVendorId, UUID buyerVendorId) {
        Set<String> values = redis.opsForSet().intersect(
                eventInventoryCardsKey(eventId, sellerVendorId),
                eventDemandCardsKey(eventId, buyerVendorId));
        return uuidSet(values == null ? Set.of() : values);
    }

    public List<InventorySnapshot> inventoryForCard(
            UUID eventId, UUID vendorId, UUID cardId) {
        Set<String> itemIds = new HashSet<>(members(
                inventorySourceCardItemsKey(null, vendorId, cardId)));
        itemIds.addAll(members(inventorySourceCardItemsKey(eventId, vendorId, cardId)));
        List<InventorySnapshot> result = new ArrayList<>();
        for (String itemId : itemIds) {
            InventorySnapshot global = readInventory("global", itemId);
            if (eligible(global, eventId, vendorId, cardId)) {
                result.add(global);
            }
            InventorySnapshot scoped = readInventory(eventId.toString(), itemId);
            if (eligible(scoped, eventId, vendorId, cardId)) {
                result.add(scoped);
            }
        }
        return List.copyOf(result);
    }

    public List<DemandSnapshot> demandForCard(UUID vendorId, UUID cardId) {
        List<DemandSnapshot> result = new ArrayList<>();
        for (String wantedId : members(demandSourceCardItemsKey(vendorId, cardId))) {
            Map<String, String> values = hashes.entries(demandSnapshotKey(UUID.fromString(wantedId)));
            if (!values.isEmpty() && Boolean.parseBoolean(values.get("active"))) {
                DemandSnapshot snapshot = demand(values);
                if (snapshot.vendorId().equals(vendorId) && snapshot.cardId().equals(cardId)) {
                    result.add(snapshot);
                }
            }
        }
        return List.copyOf(result);
    }

    private void materializeVendor(UUID eventId, UUID vendorId) {
        String inventoryKey = eventInventoryCardsKey(eventId, vendorId);
        String demandKey = eventDemandCardsKey(eventId, vendorId);
        redis.delete(List.of(inventoryKey, demandKey));

        Set<String> inventory = new HashSet<>(members(inventorySourceCardsKey(null, vendorId)));
        inventory.addAll(members(inventorySourceCardsKey(eventId, vendorId)));
        addAll(inventoryKey, inventory);
        addAll(demandKey, members(demandSourceCardsKey(vendorId)));
    }

    private void refreshInventoryCard(UUID eventId, UUID vendorId, UUID cardId) {
        String materialized = eventInventoryCardsKey(eventId, vendorId);
        boolean present = hasMembers(inventorySourceCardItemsKey(null, vendorId, cardId))
                || hasMembers(inventorySourceCardItemsKey(eventId, vendorId, cardId));
        setMembership(materialized, cardId.toString(), present);
    }

    private void refreshDemandCard(UUID eventId, UUID vendorId, UUID cardId) {
        setMembership(eventDemandCardsKey(eventId, vendorId), cardId.toString(),
                hasMembers(demandSourceCardItemsKey(vendorId, cardId)));
    }

    private void setMembership(String key, String value, boolean present) {
        if (present) {
            redis.opsForSet().add(key, value);
        } else {
            redis.opsForSet().remove(key, value);
        }
    }

    private void removeCardWhenEmpty(String itemKey, String cardsKey, String cardId) {
        if (!hasMembers(itemKey)) {
            redis.opsForSet().remove(cardsKey, cardId);
        }
    }

    private boolean hasMembers(String key) {
        Long size = redis.opsForSet().size(key);
        return size != null && size > 0;
    }

    private void addAll(String key, Collection<String> values) {
        if (!values.isEmpty()) {
            redis.opsForSet().add(key, values.toArray(String[]::new));
        }
    }

    private Set<String> members(String key) {
        Set<String> values = redis.opsForSet().members(key);
        return values == null ? Set.of() : values;
    }

    private InventorySnapshot readInventory(String scope, String itemId) {
        Map<String, String> values = hashes.entries(
                inventorySnapshotKey(scope, UUID.fromString(itemId)));
        return values.isEmpty() ? null : inventory(values);
    }

    private static boolean eligible(
            InventorySnapshot snapshot, UUID eventId, UUID vendorId, UUID cardId) {
        return snapshot != null && snapshot.active()
                && snapshot.vendorId().equals(vendorId)
                && snapshot.cardId().equals(cardId)
                && (snapshot.eventId() == null || snapshot.eventId().equals(eventId));
    }

    private static InventorySnapshot inventory(Map<String, String> values) {
        String eventId = values.get("event_id");
        return new InventorySnapshot(
                UUID.fromString(values.get("item_id")),
                UUID.fromString(values.get("vendor_id")),
                eventId == null || eventId.isBlank() ? null : UUID.fromString(eventId),
                UUID.fromString(values.get("card_id")),
                CardCondition.parse(values.get("condition")),
                Integer.parseInt(values.get("quantity")),
                new BigDecimal(values.get("asking_price")),
                InventoryPriority.parse(values.get("priority")),
                Boolean.parseBoolean(values.get("active")),
                Instant.parse(values.get("occurred_at")));
    }

    private static DemandSnapshot demand(Map<String, String> values) {
        return new DemandSnapshot(
                UUID.fromString(values.get("wanted_card_id")),
                UUID.fromString(values.get("vendor_id")),
                UUID.fromString(values.get("card_id")),
                CardCondition.parse(values.get("minimum_condition")),
                new BigDecimal(values.get("max_buy_price")),
                Integer.parseInt(values.get("quantity_wanted")),
                Boolean.parseBoolean(values.get("active")),
                Instant.parse(values.get("occurred_at")));
    }

    private static ApplyResult compare(
            Map<String, String> previous, Instant timestamp, Action action,
            Map<String, String> incomingFields) {
        if (previous.isEmpty()) {
            return ApplyResult.APPLIED;
        }
        Instant previousTimestamp = Instant.parse(previous.get("occurred_at"));
        int timeComparison = timestamp.compareTo(previousTimestamp);
        if (timeComparison < 0) {
            return ApplyResult.STALE;
        }
        if (timeComparison > 0) {
            return ApplyResult.APPLIED;
        }
        Action previousAction = Action.valueOf(previous.get("action"));
        if (actionRank(action) < actionRank(previousAction)) {
            return ApplyResult.STALE;
        }
        if (action == previousAction && fieldsMatch(previous, incomingFields)) {
            return ApplyResult.DUPLICATE;
        }
        return ApplyResult.APPLIED;
    }

    private static int actionRank(Action action) {
        return switch (action) {
            case ADDED -> 1;
            case UPDATED -> 2;
            case REMOVED -> 3;
        };
    }

    private static boolean fieldsMatch(
            Map<String, String> previous, Map<String, String> incoming) {
        return incoming.entrySet().stream()
                .allMatch(entry -> Objects.equals(previous.get(entry.getKey()), entry.getValue()));
    }

    private static Map<String, String> inventoryFields(InventoryUpdated fact) {
        Map<String, String> values = new HashMap<>();
        values.put("item_id", fact.inventoryItemId().toString());
        values.put("vendor_id", fact.vendorId().toString());
        values.put("event_id", fact.eventId() == null ? "" : fact.eventId().toString());
        values.put("card_id", fact.cardId().toString());
        values.put("condition", fact.condition().toUpperCase());
        values.put("quantity", Integer.toString(fact.quantity()));
        values.put("asking_price", fact.askingPrice().toPlainString());
        values.put("priority", fact.priority().toLowerCase());
        return values;
    }

    private static Map<String, String> demandFields(BuyListUpdated fact) {
        Map<String, String> values = new HashMap<>();
        values.put("wanted_card_id", fact.wantedCardId().toString());
        values.put("vendor_id", fact.vendorId().toString());
        values.put("card_id", fact.cardId().toString());
        values.put("minimum_condition", fact.minimumCondition().toUpperCase());
        values.put("max_buy_price", fact.maxBuyPrice().toPlainString());
        values.put("quantity_wanted", Integer.toString(fact.quantityWanted()));
        return values;
    }

    private static void validateInventory(InventoryUpdated fact) {
        Objects.requireNonNull(fact, "inventory fact is required");
        Objects.requireNonNull(fact.inventoryItemId(), "inventory_item_id is required");
        Objects.requireNonNull(fact.vendorId(), "vendor_id is required");
        Objects.requireNonNull(fact.cardId(), "card_id is required");
        CardCondition.parse(fact.condition());
        InventoryPriority.parse(fact.priority());
        requirePositive(fact.quantity(), "quantity");
        requireNonNegative(fact.askingPrice(), "asking_price");
        Objects.requireNonNull(fact.action(), "action is required");
        Objects.requireNonNull(fact.timestamp(), "timestamp is required");
    }

    private static void validateDemand(BuyListUpdated fact) {
        Objects.requireNonNull(fact, "buy-list fact is required");
        Objects.requireNonNull(fact.wantedCardId(), "wanted_card_id is required");
        Objects.requireNonNull(fact.vendorId(), "vendor_id is required");
        Objects.requireNonNull(fact.cardId(), "card_id is required");
        CardCondition.parse(fact.minimumCondition());
        requirePositive(fact.quantityWanted(), "quantity_wanted");
        requireNonNegative(fact.maxBuyPrice(), "max_buy_price");
        Objects.requireNonNull(fact.action(), "action is required");
        Objects.requireNonNull(fact.timestamp(), "timestamp is required");
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void assertImmutable(
            Map<String, String> previous, String field, String incoming, String label) {
        if (!previous.isEmpty() && !Objects.equals(previous.get(field), incoming)) {
            throw new IllegalArgumentException(label + " cannot change");
        }
    }

    private static Set<UUID> uuidSet(Collection<String> values) {
        Set<UUID> result = new HashSet<>();
        for (String value : values) {
            result.add(UUID.fromString(value));
        }
        return Set.copyOf(result);
    }

    private static String scope(UUID eventId) {
        return eventId == null ? "global" : eventId.toString();
    }

    private static String inventorySnapshotKey(String scope, UUID itemId) {
        return PREFIX + "inventory:snapshot:" + scope + ":" + itemId;
    }

    private static String demandSnapshotKey(UUID wantedCardId) {
        return PREFIX + "demand:snapshot:" + wantedCardId;
    }

    private static String rosterSnapshotKey(UUID eventId, UUID vendorId) {
        return PREFIX + "roster:snapshot:" + eventId + ":" + vendorId;
    }

    private static String eventVendorsKey(UUID eventId) {
        return PREFIX + "event:" + eventId + ":vendors";
    }

    private static String vendorEventsKey(UUID vendorId) {
        return PREFIX + "vendor:" + vendorId + ":events";
    }

    private static String eventInventoryCardsKey(UUID eventId, UUID vendorId) {
        return PREFIX + "event:" + eventId + ":vendor:" + vendorId + ":inventory";
    }

    private static String eventDemandCardsKey(UUID eventId, UUID vendorId) {
        return PREFIX + "event:" + eventId + ":vendor:" + vendorId + ":buylist";
    }

    private static String inventorySourceCardsKey(UUID eventId, UUID vendorId) {
        return eventId == null
                ? PREFIX + "vendor:" + vendorId + ":inventory:global:cards"
                : PREFIX + "event:" + eventId + ":vendor:" + vendorId
                    + ":inventory-source:cards";
    }

    private static String inventorySourceCardItemsKey(
            UUID eventId, UUID vendorId, UUID cardId) {
        return inventorySourceCardsKey(eventId, vendorId) + ":" + cardId + ":items";
    }

    private static String demandSourceCardsKey(UUID vendorId) {
        return PREFIX + "vendor:" + vendorId + ":buylist-source:cards";
    }

    private static String demandSourceCardItemsKey(UUID vendorId, UUID cardId) {
        return demandSourceCardsKey(vendorId) + ":" + cardId + ":items";
    }
}
