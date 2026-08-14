package com.vendex.inventory.importer;

import com.vendex.inventory.config.InventoryProperties;
import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryPriority;
import com.vendex.inventory.service.InventoryExceptions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CsvInventoryParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "card_name", "set_name", "condition", "quantity", "priority");

    private final InventoryProperties properties;

    public CsvInventoryParser(InventoryProperties properties) {
        this.properties = properties;
    }

    public Result parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InventoryExceptions.ValidationException("csv_content is required");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > properties.csv().maxBytes()) {
            throw new InventoryExceptions.ValidationException("CSV exceeds the configured size limit");
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(content, format)) {
            Map<String, Integer> headers = parser.getHeaderMap();
            validateHeaders(headers.keySet());
            List<ParsedRow> rows = new ArrayList<>();
            List<RejectedRow> rejected = new ArrayList<>();
            for (CSVRecord record : parser) {
                int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
                if (rows.size() + rejected.size() >= properties.csv().maxRows()) {
                    throw new InventoryExceptions.ValidationException("CSV exceeds the configured row limit");
                }
                try {
                    rows.add(parseRow(record, rowNumber, headers));
                } catch (IllegalArgumentException e) {
                    rejected.add(new RejectedRow(rowNumber,
                            safe(record, "card_name"), safe(record, "set_name"), e.getMessage()));
                }
            }
            return new Result(List.copyOf(rows), List.copyOf(rejected));
        } catch (IOException e) {
            throw new InventoryExceptions.ValidationException("CSV could not be parsed", e);
        }
    }

    private static ParsedRow parseRow(CSVRecord record, int rowNumber,
                                      Map<String, Integer> headers) {
        String cardName = required(record, "card_name");
        String setName = required(record, "set_name");
        CardCondition condition = enumValue(CardCondition.class, required(record, "condition"), "condition");
        int quantity = positiveInt(required(record, "quantity"), "quantity");
        String priceHeader = headers.containsKey("asking_price") ? "asking_price" : "price";
        BigDecimal price = nonNegativeDecimal(required(record, priceHeader), priceHeader);
        InventoryPriority priority = enumValue(
                InventoryPriority.class, required(record, "priority"), "priority");
        String gradingCompany = optional(record, headers, "grading_company");
        String gradeText = optional(record, headers, "grade");
        BigDecimal grade = gradeText == null ? null : decimal(gradeText, "grade");
        if ((gradingCompany == null) != (grade == null)) {
            throw new IllegalArgumentException("grading_company and grade must be supplied together");
        }
        if (grade != null && (grade.compareTo(BigDecimal.ONE) < 0
                || grade.compareTo(BigDecimal.TEN) > 0)) {
            throw new IllegalArgumentException("grade must be between 1 and 10");
        }
        return new ParsedRow(rowNumber, cardName, setName, condition, gradingCompany,
                grade, quantity, price, priority);
    }

    private static void validateHeaders(Set<String> headers) {
        List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !headers.contains(h)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new InventoryExceptions.ValidationException(
                    "CSV is missing required headers: " + String.join(", ", missing));
        }
        if (!headers.contains("price") && !headers.contains("asking_price")) {
            throw new InventoryExceptions.ValidationException(
                    "CSV is missing required header: price (or asking_price)");
        }
    }

    private static String required(CSVRecord record, String header) {
        String value = record.get(header);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(header + " is required");
        }
        return value.trim();
    }

    private static String optional(CSVRecord record, Map<String, Integer> headers, String header) {
        if (!headers.containsKey(header)) {
            return null;
        }
        String value = record.get(header);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safe(CSVRecord record, String header) {
        try {
            return record.isMapped(header) ? record.get(header) : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static int positiveInt(String value, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
    }

    private static BigDecimal nonNegativeDecimal(String value, String field) {
        BigDecimal parsed = decimal(value, field);
        if (parsed.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return parsed;
    }

    private static BigDecimal decimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " has an unsupported value: " + value);
        }
    }

    public record ParsedRow(
            int rowNumber,
            String cardName,
            String setName,
            CardCondition condition,
            String gradingCompany,
            BigDecimal grade,
            int quantity,
            BigDecimal askingPrice,
            InventoryPriority priority
    ) {}

    public record RejectedRow(int rowNumber, String cardName, String setName, String reason) {}
    public record Result(List<ParsedRow> rows, List<RejectedRow> rejectedRows) {}
}
