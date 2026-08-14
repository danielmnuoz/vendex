package com.vendex.inventory.importer;

import com.vendex.inventory.config.InventoryProperties;
import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryPriority;
import com.vendex.inventory.service.InventoryExceptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvInventoryParserTest {

    private final CsvInventoryParser parser = new CsvInventoryParser(properties(10, 10_000));

    @Test
    void parsesQuotedNamesAndOptionalGradingFields() {
        String csv = """
                card_name,set_name,condition,quantity,price,priority,grading_company,grade
                "Pikachu, Birthday",Celebrations,NM,2,14.50,liquidate,PSA,9.5
                """;

        CsvInventoryParser.Result result = parser.parse(csv);

        assertThat(result.rejectedRows()).isEmpty();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.cardName()).isEqualTo("Pikachu, Birthday");
            assertThat(row.condition()).isEqualTo(CardCondition.NM);
            assertThat(row.quantity()).isEqualTo(2);
            assertThat(row.askingPrice()).isEqualByComparingTo("14.50");
            assertThat(row.priority()).isEqualTo(InventoryPriority.LIQUIDATE);
            assertThat(row.grade()).isEqualByComparingTo(new BigDecimal("9.5"));
            assertThat(row.rowNumber()).isEqualTo(2);
        });
    }

    @Test
    void keepsInvalidRowsAsManualReviewIssues() {
        String csv = """
                card_name,set_name,condition,quantity,price,priority
                Pikachu,Celebrations,NM,zero,14.50,normal
                Charizard,Base Set,wat,1,100.00,normal
                """;

        CsvInventoryParser.Result result = parser.parse(csv);

        assertThat(result.rows()).isEmpty();
        assertThat(result.rejectedRows())
                .extracting(CsvInventoryParser.RejectedRow::rowNumber,
                        CsvInventoryParser.RejectedRow::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, "quantity must be a whole number"),
                        org.assertj.core.groups.Tuple.tuple(3, "condition has an unsupported value: wat"));
    }

    @Test
    void acceptsAskingPriceHeaderAlias() {
        String csv = """
                card_name,set_name,condition,quantity,asking_price,priority
                Mew,151,LP,1,9.25,normal
                """;

        assertThat(parser.parse(csv).rows()).singleElement()
                .extracting(CsvInventoryParser.ParsedRow::askingPrice)
                .isEqualTo(new BigDecimal("9.25"));
    }

    @Test
    void rejectsMissingRequiredHeader() {
        String csv = "card_name,set_name,quantity,price,priority\nPikachu,151,1,5,normal\n";

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(InventoryExceptions.ValidationException.class)
                .hasMessageContaining("condition");
    }

    @Test
    void enforcesConfiguredRowLimit() {
        CsvInventoryParser oneRowParser = new CsvInventoryParser(properties(1, 10_000));
        String csv = """
                card_name,set_name,condition,quantity,price,priority
                Pikachu,151,NM,1,5,normal
                Mew,151,NM,1,5,normal
                """;

        assertThatThrownBy(() -> oneRowParser.parse(csv))
                .isInstanceOf(InventoryExceptions.ValidationException.class)
                .hasMessageContaining("row limit");
    }

    private static InventoryProperties properties(int maxRows, int maxBytes) {
        return new InventoryProperties("unused",
                new InventoryProperties.Csv(maxRows, maxBytes),
                new InventoryProperties.Matching(0.82, 0.05));
    }
}
