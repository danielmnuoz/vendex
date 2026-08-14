package com.vendex.inventory.service;

import com.vendex.inventory.catalog.CardCatalogGateway;
import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.domain.InventoryPriority;
import com.vendex.inventory.importer.CsvInventoryParser;
import com.vendex.inventory.importer.FuzzyCardResolver;
import com.vendex.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private InventoryRepository repository;
    private InventoryWriter writer;
    private CardCatalogGateway cards;
    private CsvInventoryParser csvParser;
    private FuzzyCardResolver resolver;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        writer = mock(InventoryWriter.class);
        cards = mock(CardCatalogGateway.class);
        csvParser = mock(CsvInventoryParser.class);
        resolver = mock(FuzzyCardResolver.class);
        service = new InventoryService(repository, writer, cards, csvParser, resolver);
    }

    @Test
    void addValidatesCanonicalCardBeforeWriting() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        InventoryItemInput input = input(null);
        InventoryItem item = item(vendorId, cardId, input);
        when(cards.get(cardId)).thenReturn(card(cardId, "Pikachu", "151"));
        when(writer.add(vendorId, cardId, input)).thenReturn(item);

        assertThat(service.add(vendorId, cardId, input)).isEqualTo(item);

        verify(cards).get(cardId);
        verify(writer).add(vendorId, cardId, input);
    }

    @Test
    void invalidGradingPairNeverCallsCatalogOrWriter() {
        InventoryItemInput input = new InventoryItemInput(null, CardCondition.NM, "PSA", null,
                1, new BigDecimal("10.00"), InventoryPriority.NORMAL);

        assertThatThrownBy(() -> service.add(UUID.randomUUID(), UUID.randomUUID(), input))
                .isInstanceOf(InventoryExceptions.ValidationException.class)
                .hasMessageContaining("supplied together");

        verify(cards, never()).get(any());
        verify(writer, never()).add(any(), any(), any());
    }

    @Test
    void updateRejectsDifferentVendor() {
        UUID ownerId = UUID.randomUUID();
        InventoryItem current = item(ownerId, UUID.randomUUID(), input(null));
        when(repository.findById(current.id())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(current.id(), UUID.randomUUID(), input(null)))
                .isInstanceOf(InventoryExceptions.OwnershipException.class);

        verify(writer, never()).update(any(), any());
    }

    @Test
    void listUsesExtraRowForPagination() {
        UUID vendorId = UUID.randomUUID();
        InventoryItem first = item(vendorId, UUID.randomUUID(), input(null));
        InventoryItem second = item(vendorId, UUID.randomUUID(), input(null));
        InventoryItem third = item(vendorId, UUID.randomUUID(), input(null));
        when(repository.listForVendor(vendorId, 3, 10)).thenReturn(List.of(first, second, third));

        InventoryService.Page page = service.list(vendorId, 2, 10);

        assertThat(page.items()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPageOffset()).isEqualTo(12);
    }

    @Test
    void eventSearchRequiresCardAndNeverOffersBrowseAll() {
        UUID eventId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(cards.get(cardId)).thenReturn(card(cardId, "Mew", "151"));
        when(repository.searchForEvent(eq(eventId), eq(cardId), eq(List.of(CardCondition.NM)),
                eq(new BigDecimal("20.00")), eq(26), eq(0))).thenReturn(List.of());

        InventoryService.Page page = service.searchForEvent(eventId, cardId,
                List.of(CardCondition.NM), new BigDecimal("20.00"), 25, 0);

        assertThat(page.items()).isEmpty();
        verify(cards).get(cardId);
        verify(repository).searchForEvent(eventId, cardId, List.of(CardCondition.NM),
                new BigDecimal("20.00"), 26, 0);
        assertThatThrownBy(() -> service.searchForEvent(eventId, null,
                List.of(), null, 25, 0))
                .isInstanceOf(InventoryExceptions.ValidationException.class)
                .hasMessageContaining("card_id");
    }

    @Test
    void dryRunReturnsResolvedPreviewAndIssuesWithoutWriting() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        var parsed = new CsvInventoryParser.ParsedRow(2, "Pikachu", "151",
                CardCondition.NM, null, null, 2, new BigDecimal("5.00"),
                InventoryPriority.NORMAL);
        when(csvParser.parse("csv")).thenReturn(new CsvInventoryParser.Result(
                List.of(parsed),
                List.of(new CsvInventoryParser.RejectedRow(3, "Mew", "151", "bad quantity"))));
        var candidate = new FuzzyCardResolver.Candidate(card(cardId, "Pikachu", "151"), 1.0);
        when(resolver.resolve("Pikachu", "151")).thenReturn(
                new FuzzyCardResolver.Resolution(Optional.of(candidate), List.of(candidate), null));

        InventoryService.BulkImportResult result = service.bulkImport(vendorId, null, "csv", true);

        assertThat(result.committed()).isFalse();
        assertThat(result.importedItems()).isEmpty();
        assertThat(result.resolvedRows()).singleElement()
                .extracting(InventoryService.ResolvedRow::cardId)
                .isEqualTo(cardId);
        assertThat(result.issues()).singleElement()
                .extracting(InventoryService.ImportIssue::rowNumber)
                .isEqualTo(3);
        verify(writer, never()).addAll(any(), any());
    }

    @Test
    void commitPersistsOnlyAutoResolvedRowsAsOneBatch() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        var parsed = new CsvInventoryParser.ParsedRow(2, "Pikachu", "151",
                CardCondition.LP, null, null, 1, new BigDecimal("4.00"),
                InventoryPriority.LIQUIDATE);
        when(csvParser.parse("csv")).thenReturn(new CsvInventoryParser.Result(List.of(parsed), List.of()));
        var candidate = new FuzzyCardResolver.Candidate(card(cardId, "Pikachu", "151"), 0.98);
        when(resolver.resolve("Pikachu", "151")).thenReturn(
                new FuzzyCardResolver.Resolution(Optional.of(candidate), List.of(candidate), null));
        InventoryItem stored = item(vendorId, cardId, new InventoryItemInput(
                null, CardCondition.LP, null, null, 1, new BigDecimal("4.00"),
                InventoryPriority.LIQUIDATE));
        when(writer.addAll(eq(vendorId), any())).thenReturn(List.of(stored));

        InventoryService.BulkImportResult result = service.bulkImport(vendorId, null, "csv", false);

        assertThat(result.committed()).isTrue();
        assertThat(result.importedItems()).containsExactly(stored);
        verify(writer).addAll(eq(vendorId), org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1 && entries.getFirst().cardId().equals(cardId)));
    }

    @Test
    void invalidBusinessFieldsBecomeRowIssuesBeforeCatalogResolution() {
        UUID vendorId = UUID.randomUUID();
        var parsed = new CsvInventoryParser.ParsedRow(2, "Pikachu", "151",
                CardCondition.NM, null, null, 1, new BigDecimal("4.999"),
                InventoryPriority.NORMAL);
        when(csvParser.parse("csv")).thenReturn(
                new CsvInventoryParser.Result(List.of(parsed), List.of()));

        InventoryService.BulkImportResult result = service.bulkImport(vendorId, null, "csv", true);

        assertThat(result.resolvedRows()).isEmpty();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.rowNumber()).isEqualTo(2);
            assertThat(issue.reason()).contains("two decimal places");
        });
        verify(resolver, never()).resolve(any(), any());
        verify(writer, never()).addAll(any(), any());
    }

    private static InventoryItemInput input(UUID eventId) {
        return new InventoryItemInput(eventId, CardCondition.NM, null, null,
                2, new BigDecimal("14.50"), InventoryPriority.NORMAL);
    }

    private static InventoryItem item(UUID vendorId, UUID cardId, InventoryItemInput input) {
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        return new InventoryItem(UUID.randomUUID(), vendorId, cardId, input.eventId(),
                input.condition(), input.gradingCompany(), input.grade(), input.quantity(),
                input.askingPrice(), input.priority(), now, now);
    }

    private static CardCatalogGateway.CanonicalCard card(UUID id, String name, String setName) {
        return new CardCatalogGateway.CanonicalCard(id, "external", name, "set-id", setName);
    }
}
