package com.vendex.buylist.service;

import com.vendex.buylist.domain.CardCondition;
import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import com.vendex.buylist.repository.BuyListRepository;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyListWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private BuyListRepository repository;
    private OutboxWriter outbox;
    private BuyListWriter writer;

    @BeforeEach
    void setUp() {
        repository = mock(BuyListRepository.class);
        outbox = mock(OutboxWriter.class);
        writer = new BuyListWriter(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addWritesTheBuyListFact() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        WantedCard wantedCard = wantedCard(vendorId, cardId);
        when(repository.insert(vendorId, cardId, input(), NOW)).thenReturn(wantedCard);

        writer.add(vendorId, cardId, input());

        verify(outbox).write(eq("wanted_card"), eq(wantedCard.id().toString()),
                eq(Topics.BUYLIST_UPDATED), eq(vendorId.toString()),
                eq(new BuyListUpdated(vendorId, cardId, Action.ADDED, NOW)));
    }

    @Test
    void duplicateVendorCardGetsAStableDomainError() {
        when(repository.insert(any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> writer.add(UUID.randomUUID(), UUID.randomUUID(), input()))
                .isInstanceOf(BuyListExceptions.AlreadyWantedException.class);
    }

    @Test
    void updateAndRemovePublishTheirActions() {
        WantedCard current = wantedCard(UUID.randomUUID(), UUID.randomUUID());
        WantedCardInput changed = new WantedCardInput(
                CardCondition.MP, new BigDecimal("30.00"), 3);
        WantedCard updated = new WantedCard(current.id(), current.vendorId(), current.cardId(),
                changed.minimumCondition(), changed.maxBuyPrice(), changed.quantityWanted(),
                current.createdAt(), NOW);
        when(repository.update(current.id(), changed, NOW)).thenReturn(Optional.of(updated));
        when(repository.delete(current.id())).thenReturn(Optional.of(updated));

        writer.update(current, changed);
        writer.remove(updated);

        verify(outbox).write(eq("wanted_card"), eq(current.id().toString()),
                eq(Topics.BUYLIST_UPDATED), eq(current.vendorId().toString()),
                eq(new BuyListUpdated(current.vendorId(), current.cardId(), Action.UPDATED, NOW)));
        verify(outbox).write(eq("wanted_card"), eq(current.id().toString()),
                eq(Topics.BUYLIST_UPDATED), eq(current.vendorId().toString()),
                eq(new BuyListUpdated(current.vendorId(), current.cardId(), Action.REMOVED, NOW)));
    }

    private static WantedCardInput input() {
        return new WantedCardInput(CardCondition.LP, new BigDecimal("25.00"), 2);
    }

    private static WantedCard wantedCard(UUID vendorId, UUID cardId) {
        return new WantedCard(UUID.randomUUID(), vendorId, cardId, CardCondition.LP,
                new BigDecimal("25.00"), 2, NOW, NOW);
    }
}
