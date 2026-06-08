package com.vendex.auth.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AeadTest {

    private static final byte[] KEY = new byte[32];
    static {
        new SecureRandom().nextBytes(KEY);
    }

    @Test
    void encrypts_and_decrypts_round_trip() {
        Aead aead = Aead.fromRawKey(KEY);
        byte[] plaintext = "the quick brown fox".getBytes();
        byte[] aad = "row-uuid-bytes".getBytes();

        byte[] ciphertext = aead.encrypt(plaintext, aad);
        byte[] recovered = aead.decrypt(ciphertext, aad);

        assertThat(recovered).isEqualTo(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
        // 12-byte IV prefix + plaintext + 16-byte GCM tag
        assertThat(ciphertext).hasSize(12 + plaintext.length + 16);
    }

    @Test
    void wrong_aad_fails_decryption() {
        Aead aead = Aead.fromRawKey(KEY);
        byte[] ciphertext = aead.encrypt("secret".getBytes(), "row-A".getBytes());

        assertThatThrownBy(() -> aead.decrypt(ciphertext, "row-B".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AAD mismatch");
    }

    @Test
    void tampered_ciphertext_fails_decryption() {
        Aead aead = Aead.fromRawKey(KEY);
        byte[] ciphertext = aead.encrypt("secret".getBytes(), null);
        ciphertext[ciphertext.length - 1] ^= 0x01;   // flip a bit in the GCM tag

        assertThatThrownBy(() -> aead.decrypt(ciphertext, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrong_key_size_rejected_at_construction() {
        assertThatThrownBy(() -> Aead.fromRawKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32-byte");
    }

    @Test
    void uninitialized_aead_throws_on_use() {
        Aead aead = Aead.uninitialized();
        assertThatThrownBy(() -> aead.encrypt(new byte[]{1, 2, 3}, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uninitialized");
    }
}
