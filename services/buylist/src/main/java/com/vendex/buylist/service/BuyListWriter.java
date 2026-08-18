package com.vendex.buylist.service;

import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import com.vendex.buylist.repository.BuyListRepository;
import com.vendex.events.contract.Action;
import com.vendex.events.contract.BuyListUpdated;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class BuyListWriter {

    private final BuyListRepository repository;
    private final OutboxWriter outbox;
    private final Clock clock;

    public BuyListWriter(BuyListRepository repository, OutboxWriter outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public WantedCard add(UUID vendorId, UUID cardId, WantedCardInput input) {
        Instant now = clock.instant();
        WantedCard wantedCard;
        try {
            wantedCard = repository.insert(vendorId, cardId, input, now);
        } catch (DuplicateKeyException e) {
            throw new BuyListExceptions.AlreadyWantedException();
        }
        publish(wantedCard, Action.ADDED, now);
        return wantedCard;
    }

    @Transactional
    public WantedCard update(WantedCard current, WantedCardInput input) {
        Instant now = clock.instant();
        WantedCard updated = repository.update(current.id(), input, now)
                .orElseThrow(BuyListExceptions.WantedCardNotFoundException::new);
        publish(updated, Action.UPDATED, now);
        return updated;
    }

    @Transactional
    public WantedCard remove(WantedCard current) {
        Instant now = clock.instant();
        WantedCard removed = repository.delete(current.id())
                .orElseThrow(BuyListExceptions.WantedCardNotFoundException::new);
        publish(removed, Action.REMOVED, now);
        return removed;
    }

    private void publish(WantedCard wantedCard, Action action, Instant timestamp) {
        outbox.write("wanted_card", wantedCard.id().toString(), Topics.BUYLIST_UPDATED,
                wantedCard.vendorId().toString(),
                new BuyListUpdated(wantedCard.id(), wantedCard.vendorId(), wantedCard.cardId(),
                        wantedCard.minimumCondition().name(), wantedCard.maxBuyPrice(),
                        wantedCard.quantityWanted(), action, timestamp));
    }
}
