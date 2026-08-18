package com.vendex.overlap.service;

import com.vendex.events.contract.OverlapSaved;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.domain.SavedOverlap;
import com.vendex.overlap.repository.OverlapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OverlapQueryService {
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final OverlapRepository repository;
    private final Clock clock;
    private final OutboxWriter outbox;

    public OverlapQueryService(OverlapRepository repository, Clock clock, OutboxWriter outbox) {
        this.repository = repository;
        this.clock = clock;
        this.outbox = outbox;
    }

    public OverlapPage getForVendor(
            UUID vendorId, UUID eventId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        requireId(eventId, "event_id");
        int pageSize = pageSize(requestedPageSize, offset);
        return overlapPage(repository.listForVendor(
                vendorId, eventId, pageSize + 1, offset), pageSize, offset);
    }

    @Transactional
    public SavedOverlap save(UUID vendorId, UUID overlapId) {
        requireId(vendorId, "vendor_id");
        requireId(overlapId, "overlap_id");
        Overlap overlap = repository.findById(overlapId)
                .orElseThrow(OverlapExceptions.NotFoundException::new);
        if (!overlap.buyerVendorId().equals(vendorId)
                && !overlap.sellerVendorId().equals(vendorId)) {
            throw new OverlapExceptions.OwnershipException();
        }
        if (!overlap.active()) {
            throw new OverlapExceptions.InactiveException();
        }
        var result = repository.save(vendorId, overlap, clock.instant());
        if (result.created()) {
            SavedOverlap saved = result.savedOverlap();
            outbox.write("saved_overlap", saved.id().toString(), Topics.OVERLAP_SAVED,
                    vendorId.toString(), new OverlapSaved(
                            saved.id(), overlap.id(), vendorId, overlap.eventId(),
                            overlap.buyerVendorId(), overlap.sellerVendorId(),
                            overlap.cardId(), overlap.score(), saved.createdAt()));
        }
        return result.savedOverlap();
    }

    public SavedPage listSaved(
            UUID vendorId, UUID eventId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        requireId(eventId, "event_id");
        int pageSize = pageSize(requestedPageSize, offset);
        return savedPage(repository.listSaved(
                vendorId, eventId, pageSize + 1, offset), pageSize, offset);
    }

    private static int pageSize(int requested, int offset) {
        if (offset < 0) {
            throw new OverlapExceptions.ValidationException(
                    "page_offset must not be negative");
        }
        return requested <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, MAX_PAGE_SIZE);
    }

    private static OverlapPage overlapPage(List<Overlap> rows, int pageSize, int offset) {
        boolean hasMore = rows.size() > pageSize;
        List<Overlap> page = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize)) : new ArrayList<>(rows);
        return new OverlapPage(List.copyOf(page), hasMore ? offset + pageSize : 0, hasMore);
    }

    private static SavedPage savedPage(
            List<SavedOverlap> rows, int pageSize, int offset) {
        boolean hasMore = rows.size() > pageSize;
        List<SavedOverlap> page = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize)) : new ArrayList<>(rows);
        return new SavedPage(List.copyOf(page), hasMore ? offset + pageSize : 0, hasMore);
    }

    private static void requireId(UUID value, String field) {
        if (value == null) {
            throw new OverlapExceptions.ValidationException(field + " is required");
        }
    }

    public record OverlapPage(List<Overlap> overlaps, int nextPageOffset, boolean hasMore) {}
    public record SavedPage(List<SavedOverlap> saved, int nextPageOffset, boolean hasMore) {}
}
