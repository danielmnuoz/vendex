package jwt

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"time"

	"github.com/danielmnuoz/vendex/services/auth/internal/crypto"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/google/uuid"
)

// GenerateAndStoreKey creates a new RSA-2048 keypair, encrypts the private
// key with the supplied AEAD, and inserts it as the new active signing key.
// Any previously-active key is marked rotated (it stays in JWKS until
// every JWT it signed has expired).
func GenerateAndStoreKey(ctx context.Context, s store.Store, a *crypto.AEAD) (store.SigningKey, error) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return store.SigningKey{}, fmt.Errorf("generate rsa key: %w", err)
	}
	privPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "RSA PRIVATE KEY",
		Bytes: x509.MarshalPKCS1PrivateKey(priv),
	})
	pubBytes, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		return store.SigningKey{}, fmt.Errorf("marshal public key: %w", err)
	}
	pubPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: pubBytes,
	})
	encryptedPriv, err := a.Encrypt(privPEM)
	if err != nil {
		return store.SigningKey{}, fmt.Errorf("encrypt private key: %w", err)
	}

	now := time.Now().UTC()

	// Mark any existing active key as rotated, in a best-effort way.
	if existing, err := s.GetActiveSigningKey(ctx); err == nil {
		if rotateErr := s.MarkSigningKeyRotated(ctx, existing.ID, now); rotateErr != nil {
			return store.SigningKey{}, fmt.Errorf("rotate previous key: %w", rotateErr)
		}
	}

	k := store.SigningKey{
		ID:                  uuid.New(),
		PublicKeyPEM:        string(pubPEM),
		PrivateKeyEncrypted: encryptedPriv,
		Alg:                 "RS256",
		CreatedAt:           now,
	}
	if err := s.InsertSigningKey(ctx, k); err != nil {
		return store.SigningKey{}, fmt.Errorf("insert key: %w", err)
	}
	return k, nil
}
