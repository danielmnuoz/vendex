package com.vendex.buylist.service;

import com.vendex.buylist.catalog.CardCatalogGateway;
import com.vendex.buylist.domain.CardCondition;
import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import com.vendex.buylist.repository.BuyListRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyListServiceTest {

    private BuyListRepository repository;
    private BuyListWriter writer;
    private CardCatalogGateway cards;
    private BuyListService service;

    @BeforeEach
    void setUp() {
        repository = mock(BuyListRepository.class);
        writer = mock(BuyListWriter.class);
        cards = mock(CardCatalogGateway.class);
        service = new BuyListService(repository, writer, cards);
    }

    @Test
    void addValidatesCanonicalCardBeforeWriting() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        WantedCardInput input = input();
        WantedCard wantedCard = wantedCard(vendorId, cardId, input);
        when(cards.get(cardId)).thenReturn(card(cardId));
        when(writer.add(vendorId, cardId, input)).thenReturn(wantedCard);

        assertThat(service.add(vendorId, cardId, input)).isEqualTo(wantedCard);
        verify(cards).get(cardId);
        verify(writer).add(vendorId, cardId, input);
    }

    @Test
    void invalidMoneyAndQuantityNeverCallCatalogOrWriter() {
        WantedCardInput invalid = new WantedCardInput(
                CardCondition.LP, new BigDecimal("10.999"), 0);

        assertThatThrownBy(() -> service.add(UUID.randomUUID(), UUID.randomUUID(), invalid))
                .isInstanceOf(BuyListExceptions.ValidationException.class);
        verify(cards, never()).get(any());
        verify(writer, never()).add(any(), any(), any());
    }

    @Test
    void updateRejectsDifferentVendor() {
        WantedCard current = wantedCard(UUID.randomUUID(), UUID.randomUUID(), input());
        when(repository.findById(current.id())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(current.id(), UUID.randomUUID(), input()))
                .isInstanceOf(BuyListExceptions.OwnershipException.class);
        verify(writer, never()).update(any(), any());
    }

    @Test
    void listUsesOneExtraRowForPagination() {
        UUID vendorId = UUID.randomUUID();
        WantedCard first = wantedCard(vendorId, UUID.randomUUID(), input());
        WantedCard second = wantedCard(vendorId, UUID.randomUUID(), input());
        WantedCard third = wantedCard(vendorId, UUID.randomUUID(), input());
        when(repository.listForVendor(vendorId, 3, 5))
                .thenReturn(List.of(first, second, third));

        BuyListService.Page page = service.list(vendorId, 2, 5);

        assertThat(page.wantedCards()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPageOffset()).isEqualTo(7);
    }

    @Test
    void eventDemandIsBrowseableAndCardFilterIsValidatedWhenPresent() {
        UUID eventId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        BigDecimal minPrice = new BigDecimal("20.00");
        when(cards.get(cardId)).thenReturn(card(cardId));
        when(repository.listForEvent(eventId, cardId, List.of(CardCondition.NM),
                minPrice, 26, 0)).thenReturn(List.of());

        assertThat(service.listForEvent(eventId, cardId, List.of(CardCondition.NM),
                minPrice, 25, 0).wantedCards()).isEmpty();
        verify(cards).get(cardId);

        service.listForEvent(eventId, null, List.of(), null, 25, 0);
        verify(repository).listForEvent(eventId, null, List.of(), null, 26, 0);
    }

    private static WantedCardInput input() {
        return new WantedCardInput(CardCondition.LP, new BigDecimal("25.00"), 2);
    }

    private static WantedCard wantedCard(UUID vendorId, UUID cardId, WantedCardInput input) {
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        return new WantedCard(UUID.randomUUID(), vendorId, cardId, input.minimumCondition(),
                input.maxBuyPrice(), input.quantityWanted(), now, now);
    }

    private static CardCatalogGateway.CanonicalCard card(UUID id) {
        return new CardCatalogGateway.CanonicalCard(
                id, "external", "Pikachu", "sv03", "Obsidian Flames");
    }
}
