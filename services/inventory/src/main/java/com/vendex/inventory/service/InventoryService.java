package com.vendex.inventory.service;

import com.vendex.inventory.catalog.CardCatalogGateway;
import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.domain.InventoryPriority;
import com.vendex.inventory.importer.CsvInventoryParser;
import com.vendex.inventory.importer.FuzzyCardResolver;
import com.vendex.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final InventoryRepository repository;
    private final InventoryWriter writer;
    private final CardCatalogGateway cards;
    private final CsvInventoryParser csvParser;
    private final FuzzyCardResolver resolver;

    public InventoryService(InventoryRepository repository,
                            InventoryWriter writer,
                            CardCatalogGateway cards,
                            CsvInventoryParser csvParser,
                            FuzzyCardResolver resolver) {
        this.repository = repository;
        this.writer = writer;
        this.cards = cards;
        this.csvParser = csvParser;
        this.resolver = resolver;
    }

    public InventoryItem add(UUID vendorId, UUID cardId, InventoryItemInput rawInput) {
        requireId(vendorId, "vendor_id");
        requireId(cardId, "card_id");
        InventoryItemInput input = validateAndNormalize(rawInput);
        cards.get(cardId);
        return writer.add(vendorId, cardId, input);
    }

    public InventoryItem update(UUID itemId, UUID vendorId, InventoryItemInput rawInput) {
        requireId(itemId, "item_id");
        requireId(vendorId, "vendor_id");
        InventoryItemInput input = validateAndNormalize(rawInput);
        InventoryItem current = owned(itemId, vendorId);
        return writer.update(current, input);
    }

    public InventoryItem remove(UUID itemId, UUID vendorId) {
        requireId(itemId, "item_id");
        requireId(vendorId, "vendor_id");
        InventoryItem current = owned(itemId, vendorId);
        return writer.remove(current);
    }

    public Page list(UUID vendorId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        int pageSize = pageSize(requestedPageSize, offset);
        return page(repository.listForVendor(vendorId, pageSize + 1, offset), pageSize, offset);
    }

    public Page listForEvent(UUID vendorId, UUID eventId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        requireId(eventId, "event_id");
        int pageSize = pageSize(requestedPageSize, offset);
        return page(repository.listForVendorAndEvent(
                vendorId, eventId, pageSize + 1, offset), pageSize, offset);
    }

    public Page searchForEvent(UUID eventId, UUID cardId, List<CardCondition> conditions,
                               BigDecimal maxAskingPrice, int requestedPageSize, int offset) {
        requireId(eventId, "event_id");
        requireId(cardId, "card_id");
        validateOptionalPrice(maxAskingPrice, "max_asking_price");
        cards.get(cardId);
        int pageSize = pageSize(requestedPageSize, offset);
        return page(repository.searchForEvent(eventId, cardId, conditions, maxAskingPrice,
                pageSize + 1, offset), pageSize, offset);
    }

    /**
     * Resolve every valid CSV row before the first JDBC write. That keeps a
     * slow catalog dependency outside the database part of the transaction;
     * once persistence starts, all imported rows and outbox facts commit as a
     * single unit.
     */
    public BulkImportResult bulkImport(UUID vendorId, UUID eventId, String csvContent,
                                       boolean dryRun) {
        requireId(vendorId, "vendor_id");
        CsvInventoryParser.Result parsed = csvParser.parse(csvContent);
        List<ImportIssue> issues = new ArrayList<>();
        parsed.rejectedRows().forEach(row -> issues.add(new ImportIssue(
                row.rowNumber(), row.cardName(), row.setName(), row.reason(), List.of())));

        List<ResolvedRow> resolved = new ArrayList<>();
        for (CsvInventoryParser.ParsedRow row : parsed.rows()) {
            InventoryItemInput input;
            try {
                input = validateAndNormalize(new InventoryItemInput(
                        eventId, row.condition(), row.gradingCompany(), row.grade(), row.quantity(),
                        row.askingPrice(), row.priority()));
            } catch (InventoryExceptions.ValidationException e) {
                issues.add(new ImportIssue(row.rowNumber(), row.cardName(), row.setName(),
                        e.getMessage(), List.of()));
                continue;
            }
            FuzzyCardResolver.Resolution resolution = resolver.resolve(row.cardName(), row.setName());
            List<MatchCandidate> candidates = resolution.candidates().stream()
                    .map(candidate -> new MatchCandidate(
                            candidate.card().id(), candidate.card().name(),
                            candidate.card().setName(), candidate.confidence()))
                    .toList();
            if (resolution.match().isEmpty()) {
                issues.add(new ImportIssue(row.rowNumber(), row.cardName(), row.setName(),
                        resolution.reason(), candidates));
                continue;
            }
            FuzzyCardResolver.Candidate match = resolution.match().orElseThrow();
            resolved.add(new ResolvedRow(row.rowNumber(), match.card().id(),
                    match.card().name(), match.card().setName(), input, match.confidence()));
        }

        List<InventoryItem> imported = dryRun
                ? List.of()
                : writer.addAll(vendorId, resolved.stream()
                        .map(row -> new InventoryWriter.BatchEntry(row.cardId(), row.input()))
                        .toList());
        return new BulkImportResult(imported, List.copyOf(resolved),
                List.copyOf(issues), !dryRun);
    }

    private InventoryItem owned(UUID itemId, UUID vendorId) {
        InventoryItem item = repository.findById(itemId)
                .orElseThrow(InventoryExceptions.ItemNotFoundException::new);
        if (!item.vendorId().equals(vendorId)) {
            throw new InventoryExceptions.OwnershipException();
        }
        return item;
    }

    private static InventoryItemInput validateAndNormalize(InventoryItemInput input) {
        if (input == null) {
            throw new InventoryExceptions.ValidationException("inventory item fields are required");
        }
        if (input.condition() == null) {
            throw new InventoryExceptions.ValidationException("condition is required");
        }
        if (input.priority() == null) {
            throw new InventoryExceptions.ValidationException("priority is required");
        }
        if (input.quantity() <= 0) {
            throw new InventoryExceptions.ValidationException("quantity must be greater than zero");
        }
        validatePrice(input.askingPrice(), "asking_price");

        String company = blankToNull(input.gradingCompany());
        if (company != null && company.length() > 32) {
            throw new InventoryExceptions.ValidationException(
                    "grading_company must be 32 characters or fewer");
        }
        if ((company == null) != (input.grade() == null)) {
            throw new InventoryExceptions.ValidationException(
                    "grading_company and grade must be supplied together");
        }
        if (input.grade() != null) {
            if (input.grade().compareTo(BigDecimal.ONE) < 0
                    || input.grade().compareTo(BigDecimal.TEN) > 0) {
                throw new InventoryExceptions.ValidationException("grade must be between 1 and 10");
            }
            if (decimalPlaces(input.grade()) > 1) {
                throw new InventoryExceptions.ValidationException("grade supports at most one decimal place");
            }
        }
        return new InventoryItemInput(input.eventId(), input.condition(), company,
                input.grade(), input.quantity(), input.askingPrice(), input.priority());
    }

    private static void validateOptionalPrice(BigDecimal value, String field) {
        if (value != null) {
            validatePrice(value, field);
        }
    }

    private static void validatePrice(BigDecimal value, String field) {
        if (value == null) {
            throw new InventoryExceptions.ValidationException(field + " is required");
        }
        if (value.signum() < 0) {
            throw new InventoryExceptions.ValidationException(field + " must not be negative");
        }
        if (decimalPlaces(value) > 2) {
            throw new InventoryExceptions.ValidationException(field + " supports at most two decimal places");
        }
        if (value.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new InventoryExceptions.ValidationException(field + " exceeds the supported maximum");
        }
    }

    private static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static int pageSize(int requested, int offset) {
        if (offset < 0) {
            throw new InventoryExceptions.ValidationException("page_offset must not be negative");
        }
        return requested <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, MAX_PAGE_SIZE);
    }

    private static Page page(List<InventoryItem> rows, int pageSize, int offset) {
        boolean hasMore = rows.size() > pageSize;
        List<InventoryItem> items = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : new ArrayList<>(rows);
        return new Page(List.copyOf(items), hasMore ? offset + pageSize : 0, hasMore);
    }

    private static void requireId(UUID value, String field) {
        if (value == null) {
            throw new InventoryExceptions.ValidationException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Page(List<InventoryItem> items, int nextPageOffset, boolean hasMore) {}
    public record MatchCandidate(UUID cardId, String cardName, String setName, double confidence) {}
    public record ImportIssue(int rowNumber, String cardName, String setName, String reason,
                              List<MatchCandidate> candidates) {}
    public record ResolvedRow(int rowNumber, UUID cardId, String cardName, String setName,
                              InventoryItemInput input, double confidence) {}
    public record BulkImportResult(List<InventoryItem> importedItems,
                                   List<ResolvedRow> resolvedRows,
                                   List<ImportIssue> issues,
                                   boolean committed) {}
}
