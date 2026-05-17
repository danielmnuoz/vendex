// Package crypto provides AES-256-GCM helpers for encrypting RSA private keys
// at rest. The encryption key is supplied via env var (AUTH_KEY_ENCRYPTION_KEY)
// and must be 32 bytes when base64-decoded.
package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
)

type AEAD struct {
	gcm cipher.AEAD
}

func NewAEAD(keyB64 string) (*AEAD, error) {
	key, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return nil, fmt.Errorf("decode key: %w", err)
	}
	if len(key) != 32 {
		return nil, fmt.Errorf("key must be 32 bytes, got %d", len(key))
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("new cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("new gcm: %w", err)
	}
	return &AEAD{gcm: gcm}, nil
}

// Encrypt returns base64(nonce || ciphertext). The aad argument binds the
// ciphertext to its context (e.g., the signing-key UUID) — decryption with
// a different aad fails authentication. Pass nil if no binding is needed.
func (a *AEAD) Encrypt(plaintext, aad []byte) (string, error) {
	nonce := make([]byte, a.gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", fmt.Errorf("read nonce: %w", err)
	}
	ct := a.gcm.Seal(nil, nonce, plaintext, aad)
	out := append(nonce, ct...)
	return base64.StdEncoding.EncodeToString(out), nil
}

// Decrypt verifies that the ciphertext was produced by Encrypt with the
// same aad. Returns an auth error if the aad doesn't match.
func (a *AEAD) Decrypt(b64 string, aad []byte) ([]byte, error) {
	raw, err := base64.StdEncoding.DecodeString(b64)
	if err != nil {
		return nil, fmt.Errorf("decode: %w", err)
	}
	nonceSize := a.gcm.NonceSize()
	if len(raw) < nonceSize {
		return nil, errors.New("ciphertext too short")
	}
	nonce, ct := raw[:nonceSize], raw[nonceSize:]
	pt, err := a.gcm.Open(nil, nonce, ct, aad)
	if err != nil {
		return nil, fmt.Errorf("decrypt: %w", err)
	}
	return pt, nil
}
