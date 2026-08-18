package com.vendex.gateway.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.jwk.RSAKey;
import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.auth.v1.GetJWKSRequest;
import com.vendex.gateway.config.GatewayProperties;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class AuthJwksProvider {

    private final AuthServiceGrpc.AuthServiceBlockingStub auth;
    private final GatewayProperties properties;
    private final Clock clock;
    private volatile Snapshot snapshot = Snapshot.empty();

    public AuthJwksProvider(
            AuthServiceGrpc.AuthServiceBlockingStub auth,
            GatewayProperties properties,
            Clock clock) {
        this.auth = auth;
        this.properties = properties;
        this.clock = clock;
    }

    public RSAPublicKey keyFor(String kid) {
        Instant now = clock.instant();
        Snapshot current = snapshot;
        RSAPublicKey cached = current.keys().get(kid);
        if (cached != null && now.isBefore(current.refreshAt())) {
            return cached;
        }

        synchronized (this) {
            now = clock.instant();
            current = snapshot;
            cached = current.keys().get(kid);
            if (cached != null && now.isBefore(current.refreshAt())) {
                return cached;
            }
            try {
                Snapshot refreshed = refresh(now);
                RSAPublicKey key = refreshed.keys().get(kid);
                if (key == null) {
                    throw new InvalidAccessTokenException();
                }
                return key;
            } catch (StatusRuntimeException | IllegalArgumentException error) {
                if (cached != null && now.isBefore(current.staleUntil())) {
                    return cached;
                }
                throw new JwksUnavailableException(error);
            }
        }
    }

    private Snapshot refresh(Instant now) {
        var response = auth.withDeadlineAfter(properties.grpcDeadline().toMillis(), TimeUnit.MILLISECONDS)
                .getJWKS(GetJWKSRequest.getDefaultInstance());
        Map<String, RSAPublicKey> keys = new HashMap<>();
        response.getKeysList().forEach(jwk -> parse(jwk.getKid(), jwk.getAlg(), jwk.getKty(), jwk.getN(), jwk.getE())
                .ifPresent(key -> keys.put(jwk.getKid(), key)));
        Snapshot refreshed = new Snapshot(
                Map.copyOf(keys),
                now.plus(properties.jwt().cacheTtl()),
                now.plus(properties.jwt().staleTtl()));
        snapshot = refreshed;
        return refreshed;
    }

    private static Optional<RSAPublicKey> parse(String kid, String alg, String kty, String n, String e) {
        if (kid.isBlank() || !JWSAlgorithm.RS256.getName().equals(alg) || !"RSA".equals(kty)) {
            return Optional.empty();
        }
        try {
            RSAKey key = new RSAKey.Builder(new Base64URL(n), new Base64URL(e))
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return Optional.of(key.toRSAPublicKey());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private record Snapshot(Map<String, RSAPublicKey> keys, Instant refreshAt, Instant staleUntil) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Instant.MIN, Instant.MIN);
        }
    }
}
