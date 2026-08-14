package com.vendex.buylist.service;

import com.vendex.buylist.catalog.CardCatalogGateway;
import com.vendex.buylist.domain.CardCondition;
import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import com.vendex.buylist.repository.BuyListRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BuyListService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final BuyListRepository repository;
    private final BuyListWriter writer;
    private final CardCatalogGateway cards;

    public BuyListService(BuyListRepository repository, BuyListWriter writer,
                          CardCatalogGateway cards) {
        this.repository = repository;
        this.writer = writer;
        this.cards = cards;
    }

    public WantedCard add(UUID vendorId, UUID cardId, WantedCardInput rawInput) {
        requireId(vendorId, "vendor_id");
        requireId(cardId, "card_id");
        WantedCardInput input = validate(rawInput);
        cards.get(cardId);
        return writer.add(vendorId, cardId, input);
    }

    public WantedCard update(UUID wantedCardId, UUID vendorId, WantedCardInput rawInput) {
        requireId(wantedCardId, "wanted_card_id");
        requireId(vendorId, "vendor_id");
        WantedCardInput input = validate(rawInput);
        return writer.update(owned(wantedCardId, vendorId), input);
    }

    public WantedCard remove(UUID wantedCardId, UUID vendorId) {
        requireId(wantedCardId, "wanted_card_id");
        requireId(vendorId, "vendor_id");
        return writer.remove(owned(wantedCardId, vendorId));
    }

    public Page list(UUID vendorId, int requestedPageSize, int offset) {
        requireId(vendorId, "vendor_id");
        int pageSize = pageSize(requestedPageSize, offset);
        return page(repository.listForVendor(vendorId, pageSize + 1, offset), pageSize, offset);
    }

    public Page listForEvent(UUID eventId, UUID cardId, List<CardCondition> minimumConditions,
                             BigDecimal minMaxBuyPrice, int requestedPageSize, int offset) {
        requireId(eventId, "event_id");
        validateOptionalPrice(minMaxBuyPrice, "min_max_buy_price");
        if (cardId != null) {
            cards.get(cardId);
        }
        int pageSize = pageSize(requestedPageSize, offset);
        return page(repository.listForEvent(eventId, cardId, minimumConditions, minMaxBuyPrice,
                pageSize + 1, offset), pageSize, offset);
    }

    private WantedCard owned(UUID wantedCardId, UUID vendorId) {
        WantedCard wantedCard = repository.findById(wantedCardId)
                .orElseThrow(BuyListExceptions.WantedCardNotFoundException::new);
        if (!wantedCard.vendorId().equals(vendorId)) {
            throw new BuyListExceptions.OwnershipException();
        }
        return wantedCard;
    }

    private static WantedCardInput validate(WantedCardInput input) {
        if (input == null) {
            throw new BuyListExceptions.ValidationException("wanted card fields are required");
        }
        if (input.minimumCondition() == null) {
            throw new BuyListExceptions.ValidationException("minimum_condition is required");
        }
        if (input.quantityWanted() <= 0) {
            throw new BuyListExceptions.ValidationException(
                    "quantity_wanted must be greater than zero");
        }
        validatePrice(input.maxBuyPrice(), "max_buy_price");
        return input;
    }

    private static void validateOptionalPrice(BigDecimal value, String field) {
        if (value != null) {
            validatePrice(value, field);
        }
    }

    private static void validatePrice(BigDecimal value, String field) {
        if (value == null) {
            throw new BuyListExceptions.ValidationException(field + " is required");
        }
        if (value.signum() < 0) {
            throw new BuyListExceptions.ValidationException(field + " must not be negative");
        }
        if (Math.max(0, value.stripTrailingZeros().scale()) > 2) {
            throw new BuyListExceptions.ValidationException(
                    field + " supports at most two decimal places");
        }
        if (value.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new BuyListExceptions.ValidationException(
                    field + " exceeds the supported maximum");
        }
    }

    private static int pageSize(int requested, int offset) {
        if (offset < 0) {
            throw new BuyListExceptions.ValidationException("page_offset must not be negative");
        }
        return requested <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, MAX_PAGE_SIZE);
    }

    private static Page page(List<WantedCard> rows, int pageSize, int offset) {
        boolean hasMore = rows.size() > pageSize;
        List<WantedCard> wantedCards = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : new ArrayList<>(rows);
        return new Page(List.copyOf(wantedCards), hasMore ? offset + pageSize : 0, hasMore);
    }

    private static void requireId(UUID value, String field) {
        if (value == null) {
            throw new BuyListExceptions.ValidationException(field + " is required");
        }
    }

    public record Page(List<WantedCard> wantedCards, int nextPageOffset, boolean hasMore) {}
}
