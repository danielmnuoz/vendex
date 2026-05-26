package com.vendex.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.vendex.auth.crypto.Aead;
import com.vendex.auth.domain.SigningKey;
import com.vendex.auth.repository.SigningKeyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of RSA signing keys: generation, encrypted-at-rest
 * storage, rotation, and JWKS publication.
 *
 * <p>One key is active at a time (DB-enforced via partial unique index in
 * V3__signing_keys.sql). Rotated-but-not-revoked keys remain in the JWKS so
 * tokens they signed continue to validate until the access-token TTL
 * elapses for the last issued token.
 */
@Service
public class SigningKeyService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);
    private static final int RSA_KEY_SIZE = 2048;
    private static final String ALG = "RS256";

    private final SigningKeyRepository repository;
    private final Aead aead;
    private final Clock clock;

    public SigningKeyService(SigningKeyRepository repository, Aead aead, Clock clock) {
        this.repository = repository;
        this.aead = aead;
        this.clock = clock;
    }

    @PostConstruct
    void ensureActiveKey() {
        if (repository.findActive().isEmpty()) {
            log.info("No active signing key found, generating one");
            SigningKey generated = generateNewSigningKey();
            log.info("Generated signing key {} ({})", generated.id(), generated.alg());
        }
    }

    /** Returns the current signer's keypair (kid + RSA private key). */
    public ActiveSigner getActiveSigner() {
        SigningKey active = repository.findActive()
                .orElseThrow(() -> new IllegalStateException("no active signing key — bootstrap required"));
        RSAPrivateKey priv = decryptPrivateKey(active);
        return new ActiveSigner(active.id(), priv);
    }

    /** Resolves a signing key by its kid for token validation. */
    public Optional<RSAPublicKey> findPublicKeyByKid(UUID kid) {
        return repository.findById(kid).map(SigningKeyService::parsePublicKey);
    }

    /** Builds the JWKS payload from every key not yet revoked. */
    public JWKSet buildJwks() {
        var jwks = repository.findAllPublished().stream()
                .map(k -> (JWK) new RSAKey.Builder(parsePublicKey(k))
                        .keyID(k.id().toString())
                        .algorithm(JWSAlgorithm.RS256)
                        .keyUse(KeyUse.SIGNATURE)
                        .build())
                .toList();
        return new JWKSet(jwks);
    }

    /**
     * Generates a new RSA keypair, encrypts the private half with AAD bound
     * to the row's UUID, stores it, and returns the row. AAD binding means
     * a ciphertext blob copied into a different row will fail to decrypt —
     * defense in depth against blob-swap attacks at the database layer.
     */
    public SigningKey generateNewSigningKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(RSA_KEY_SIZE);
            KeyPair pair = gen.generateKeyPair();

            String publicPem = toPem("PUBLIC KEY", pair.getPublic().getEncoded());
            byte[] privatePem = toPem("PRIVATE KEY", pair.getPrivate().getEncoded()).getBytes(StandardCharsets.UTF_8);

            UUID id = UUID.randomUUID();
            byte[] encryptedPrivate = aead.encrypt(privatePem, uuidBytes(id));
            return repository.insert(id, publicPem, encryptedPrivate, ALG);
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate signing key", e);
        }
    }

    private RSAPrivateKey decryptPrivateKey(SigningKey key) {
        byte[] privPem = aead.decrypt(key.privateKeyEncrypted(), uuidBytes(key.id()));
        String pem = new String(privPem, StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse RSA private key for kid=" + key.id(), e);
        }
    }

    private static RSAPublicKey parsePublicKey(SigningKey key) {
        String base64 = key.publicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse RSA public key for kid=" + key.id(), e);
        }
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringWriter w = new StringWriter();
        w.write("-----BEGIN ").write(label).write("-----\n");
        // 64-char lines per RFC 7468
        for (int i = 0; i < base64.length(); i += 64) {
            w.write(base64.substring(i, Math.min(i + 64, base64.length())));
            w.write('\n');
        }
        w.write("-----END ").write(label).write("-----\n");
        return w.toString();
    }

    private static byte[] uuidBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public record ActiveSigner(UUID kid, RSAPrivateKey privateKey) {}
}
