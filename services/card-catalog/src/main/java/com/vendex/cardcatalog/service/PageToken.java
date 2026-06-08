package com.vendex.cardcatalog.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64-encoded offset cursor. Sufficient for catalog-size results (~20k
 * rows worst case); swap for a keyset cursor if listings ever need stability
 * across concurrent mutations.
 *
 * <p>An empty/blank token decodes to {@code 0}. Malformed tokens raise a
 * {@link CardExceptions.ValidationException} rather than silently resetting
 * the cursor — that prevents a typo from producing surprising pagination.
 */
public final class PageToken {

    private PageToken() {}

    public static int decode(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(raw);
            if (offset < 0) {
                throw new CardExceptions.ValidationException("page_token offset must be non-negative");
            }
            return offset;
        } catch (IllegalArgumentException e) {
            throw new CardExceptions.ValidationException("page_token is malformed");
        }
    }

    public static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }
}
