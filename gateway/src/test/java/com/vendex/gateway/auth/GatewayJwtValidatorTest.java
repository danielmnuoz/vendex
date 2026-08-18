package com.vendex.gateway.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vendex.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayJwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
    private RSAKey key;
    private AuthJwksProvider keys;
    private GatewayJwtValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        keys = mock(AuthJwksProvider.class);
        when(keys.keyFor("test-key")).thenReturn(key.toRSAPublicKey());
        validator = new GatewayJwtValidator(
                keys,
                new GatewayProperties(
                        Duration.ofSeconds(3),
                        new GatewayProperties.Services(true, "auth", "cards"),
                        new GatewayProperties.Jwt(
                                "https://auth.vendex.local",
                                Duration.ofMinutes(5),
                                Duration.ofHours(1),
                                Duration.ofSeconds(30)),
                        new GatewayProperties.RateLimit(300, 20, 120, Duration.ofMinutes(1)),
                        new GatewayProperties.Cors(List.of("http://localhost:3000"))),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validatesSignatureIssuerLifetimeSubjectAndRole() throws Exception {
        UUID userId = UUID.randomUUID();

        GatewayPrincipal principal = validator.validate(token(userId, "VENDOR", NOW.minusSeconds(1), NOW.plusSeconds(60),
                "https://auth.vendex.local", key));

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.role()).isEqualTo(GatewayRole.VENDOR);
        assertThat(principal.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsExpiredOrWrongIssuerTokens() throws Exception {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validate(token(
                userId, "VENDOR", NOW.minusSeconds(120), NOW.minusSeconds(31),
                "https://auth.vendex.local", key)))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> validator.validate(token(
                userId, "VENDOR", NOW, NOW.plusSeconds(60), "https://other.example", key)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void rejectsUnknownRolesAndSignatures() throws Exception {
        UUID userId = UUID.randomUUID();
        RSAKey other = new RSAKeyGenerator(2048).keyID("test-key").generate();

        assertThatThrownBy(() -> validator.validate(token(
                userId, "ADMIN", NOW, NOW.plusSeconds(60), "https://auth.vendex.local", key)))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> validator.validate(token(
                userId, "VENDOR", NOW, NOW.plusSeconds(60), "https://auth.vendex.local", other)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    private static String token(
            UUID userId,
            String role,
            Instant issuedAt,
            Instant expiresAt,
            String issuer,
            RSAKey signingKey) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("test-key")
                        .type(JOSEObjectType.JWT)
                        .build(),
                new JWTClaimsSet.Builder()
                        .subject(userId.toString())
                        .issuer(issuer)
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiresAt))
                        .claim("role", role)
                        .build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
