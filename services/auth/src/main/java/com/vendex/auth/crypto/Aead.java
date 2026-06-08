package com.vendex.auth.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM authenticated encryption with associated data (AAD).
 *
 * <p>Wire format of {@link #encrypt(byte[], byte[])} output:
 * <pre>
 *   bytes  0..11   : 96-bit random IV (GCM-recommended length)
 *   bytes 12..N    : ciphertext + 16-byte GCM authentication tag
 *                    (JCA appends the tag to the ciphertext)
 * </pre>
 *
 * <p>The AAD parameter is part of the integrity check but never appears in
 * the output bytes. Pass the row UUID's bytes as AAD to bind an encrypted
 * blob to the row it lives in — any attempt to copy the ciphertext into a
 * different row will fail decryption with {@code AEADBadTagException}.
 */
public final class Aead {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    private Aead(SecretKey key) {
        this.key = key;
    }

    /** Wrap an existing 32-byte (AES-256) key. */
    public static Aead fromRawKey(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalArgumentException(
                    "AES-256 requires a 32-byte key, got " + (rawKey == null ? "null" : rawKey.length + " bytes")
            );
        }
        return new Aead(new SecretKeySpec(rawKey, "AES"));
    }

    /**
     * Returns an Aead that will throw on any encrypt/decrypt call. Used by
     * test/dev profiles that never persist signing keys; SigningKeyService
     * detects the missing key and reports a clear error at the moment it
     * actually matters.
     */
    public static Aead uninitialized() {
        return new Aead(null);
    }

    public byte[] encrypt(byte[] plaintext, byte[] aad) {
        if (key == null) {
            throw new IllegalStateException(
                    "Aead is uninitialized — set auth.crypto.encryption-key to enable encryption"
            );
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] ciphertextAndTag = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_LEN + ciphertextAndTag.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ciphertextAndTag, 0, out, IV_LEN, ciphertextAndTag.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] payload, byte[] aad) {
        if (key == null) {
            throw new IllegalStateException(
                    "Aead is uninitialized — set auth.crypto.encryption-key to enable decryption"
            );
        }
        if (payload == null || payload.length <= IV_LEN) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        try {
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LEN);
            byte[] ciphertextAndTag = Arrays.copyOfRange(payload, IV_LEN, payload.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed (wrong key, tampered ciphertext, or AAD mismatch)", e);
        }
    }
}
