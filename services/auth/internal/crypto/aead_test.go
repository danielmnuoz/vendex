package crypto

import (
	"crypto/rand"
	"encoding/base64"
	"testing"
)

func newTestKeyB64(t *testing.T) string {
	t.Helper()
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		t.Fatal(err)
	}
	return base64.StdEncoding.EncodeToString(b)
}

func TestAEAD_RoundTrip(t *testing.T) {
	a, err := NewAEAD(newTestKeyB64(t))
	if err != nil {
		t.Fatalf("new aead: %v", err)
	}
	plaintexts := [][]byte{
		[]byte(""),
		[]byte("short"),
		make([]byte, 4096),
	}
	for i, pt := range plaintexts {
		ct, err := a.Encrypt(pt)
		if err != nil {
			t.Fatalf("encrypt %d: %v", i, err)
		}
		got, err := a.Decrypt(ct)
		if err != nil {
			t.Fatalf("decrypt %d: %v", i, err)
		}
		if string(got) != string(pt) {
			t.Errorf("roundtrip %d: got %q want %q", i, got, pt)
		}
	}
}

func TestAEAD_DistinctCiphertexts(t *testing.T) {
	a, _ := NewAEAD(newTestKeyB64(t))
	pt := []byte("same plaintext")
	c1, _ := a.Encrypt(pt)
	c2, _ := a.Encrypt(pt)
	if c1 == c2 {
		t.Error("expected distinct ciphertexts for repeated encryption (random nonce)")
	}
}

func TestNewAEAD_InvalidKeys(t *testing.T) {
	cases := []struct {
		name string
		key  string
	}{
		{"not_base64", "!!!not base64!!!"},
		{"wrong_length", base64.StdEncoding.EncodeToString([]byte("too short"))},
		{"empty", ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := NewAEAD(tc.key); err == nil {
				t.Error("expected error")
			}
		})
	}
}

func TestAEAD_TamperedCiphertext(t *testing.T) {
	a, _ := NewAEAD(newTestKeyB64(t))
	ct, _ := a.Encrypt([]byte("hello"))
	// Flip a byte deep in the ciphertext.
	raw, _ := base64.StdEncoding.DecodeString(ct)
	raw[len(raw)-1] ^= 0x01
	tampered := base64.StdEncoding.EncodeToString(raw)
	if _, err := a.Decrypt(tampered); err == nil {
		t.Error("expected auth failure on tampered ciphertext")
	}
}
