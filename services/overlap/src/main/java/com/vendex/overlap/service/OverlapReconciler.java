package com.vendex.overlap.service;

import com.vendex.events.contract.Action;
import com.vendex.events.contract.OverlapFound;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.overlap.config.OverlapProperties;
import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventorySnapshot;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.domain.OverlapCandidate;
import com.vendex.overlap.projection.OverlapProjectionStore;
import com.vendex.overlap.repository.OverlapRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class OverlapReconciler {

    private final OverlapProjectionStore projections;
    private final OverlapRepository repository;
    private final OverlapScorer scorer;
    private final OverlapProperties properties;
    private final OutboxWriter outbox;
    private final Clock clock;

    public OverlapReconciler(
            OverlapProjectionStore projections,
            OverlapRepository repository,
            OverlapScorer scorer,
            OverlapProperties properties,
            OutboxWriter outbox,
            Clock clock) {
        this.projections = projections;
        this.repository = repository;
        this.scorer = scorer;
        this.properties = properties;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Reconciles the directed opportunity: seller supply -> buyer demand. */
    @Transactional
    public void recompute(UUID eventId, UUID sellerId, UUID buyerId) {
        if (sellerId.equals(buyerId)
                || !projections.isVendorActive(eventId, sellerId)
                || !projections.isVendorActive(eventId, buyerId)) {
            retirePair(eventId, sellerId, buyerId);
            return;
        }

        Map<UUID, OverlapCandidate> desired = new HashMap<>();
        for (UUID cardId : projections.intersectCards(eventId, sellerId, buyerId)) {
            bestCandidate(eventId, sellerId, buyerId, cardId)
                    .filter(candidate -> candidate.score()
                            .compareTo(properties.getScoreThreshold()) > 0)
                    .ifPresent(candidate -> desired.put(cardId, candidate));
        }

        Instant now = clock.instant();
        Map<UUID, Overlap> existing = new HashMap<>();
        repository.findActivePair(eventId, sellerId, buyerId)
                .forEach(overlap -> existing.put(overlap.cardId(), overlap));

        desired.forEach((cardId, candidate) -> {
            Overlap current = existing.remove(cardId);
            if (current == null || !sameSnapshot(current, candidate)) {
                Overlap saved = repository.upsert(candidate, now);
                publish(saved, current == null ? Action.ADDED : Action.UPDATED, now);
            }
        });
        existing.values().forEach(overlap -> repository.deactivate(overlap.id(), now)
                .ifPresent(removed -> publish(removed, Action.REMOVED, now)));
    }

    @Transactional
    public void retireVendor(UUID eventId, UUID vendorId) {
        Instant now = clock.instant();
        repository.deactivateVendor(eventId, vendorId, now)
                .forEach(overlap -> publish(overlap, Action.REMOVED, now));
    }

    private void retirePair(UUID eventId, UUID sellerId, UUID buyerId) {
        Instant now = clock.instant();
        repository.deactivatePair(eventId, sellerId, buyerId, now)
                .forEach(overlap -> publish(overlap, Action.REMOVED, now));
    }

    private Optional<OverlapCandidate> bestCandidate(
            UUID eventId, UUID sellerId, UUID buyerId, UUID cardId) {
        List<InventorySnapshot> inventory = projections.inventoryForCard(
                eventId, sellerId, cardId);
        List<DemandSnapshot> demand = projections.demandForCard(buyerId, cardId);
        return inventory.stream()
                .flatMap(item -> demand.stream().map(wanted -> candidate(
                        eventId, sellerId, buyerId, cardId, item, wanted)))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(OverlapCandidate::score)
                        .thenComparing(candidate -> candidate.inventory().askingPrice(),
                                Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.inventory().itemId(),
                                Comparator.reverseOrder()));
    }

    private Optional<OverlapCandidate> candidate(
            UUID eventId, UUID sellerId, UUID buyerId, UUID cardId,
            InventorySnapshot inventory, DemandSnapshot demand) {
        return scorer.score(inventory, demand).map(score -> new OverlapCandidate(
                OverlapIds.forOpportunity(eventId, buyerId, sellerId, cardId),
                eventId, buyerId, sellerId, cardId, inventory, demand, score));
    }

    private static boolean sameSnapshot(Overlap existing, OverlapCandidate candidate) {
        return existing.inventoryItemId().equals(candidate.inventory().itemId())
                && existing.wantedCardId().equals(candidate.demand().wantedCardId())
                && existing.sellerCondition() == candidate.inventory().condition()
                && existing.minimumCondition() == candidate.demand().minimumCondition()
                && existing.availableQuantity() == candidate.inventory().quantity()
                && existing.quantityWanted() == candidate.demand().quantityWanted()
                && decimalEquals(existing.askingPrice(), candidate.inventory().askingPrice())
                && decimalEquals(existing.maxBuyPrice(), candidate.demand().maxBuyPrice())
                && existing.inventoryPriority() == candidate.inventory().priority()
                && decimalEquals(existing.score(), candidate.score());
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) == 0;
    }

    private void publish(Overlap overlap, Action action, Instant timestamp) {
        outbox.write("overlap", overlap.id().toString(), Topics.OVERLAP_FOUND,
                overlap.id().toString(), new OverlapFound(
                        overlap.id(), overlap.eventId(), overlap.buyerVendorId(),
                        overlap.sellerVendorId(), overlap.cardId(), overlap.inventoryItemId(),
                        overlap.wantedCardId(), overlap.sellerCondition().name(),
                        overlap.minimumCondition().name(), overlap.availableQuantity(),
                        overlap.quantityWanted(), overlap.askingPrice(), overlap.maxBuyPrice(),
                        overlap.inventoryPriority().name().toLowerCase(), overlap.score(),
                        action, timestamp));
    }
}
