package com.vendex.cardcatalog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageTokenTest {

    @Test
    void roundTrip() {
        assertThat(PageToken.decode(PageToken.encode(0))).isZero();
        assertThat(PageToken.decode(PageToken.encode(42))).isEqualTo(42);
        assertThat(PageToken.decode(PageToken.encode(Integer.MAX_VALUE))).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void blankDecodesToZero() {
        assertThat(PageToken.decode(null)).isZero();
        assertThat(PageToken.decode("")).isZero();
        assertThat(PageToken.decode("   ")).isZero();
    }

    @Test
    void malformedRejected() {
        assertThatThrownBy(() -> PageToken.decode("!!!"))
                .isInstanceOf(CardExceptions.ValidationException.class);
    }

    @Test
    void negativeOffsetRejected() {
        // Manually encode "-5" so the test exercises the decoded-but-invalid branch.
        String token = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("-5".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PageToken.decode(token))
                .isInstanceOf(CardExceptions.ValidationException.class);
    }
}
