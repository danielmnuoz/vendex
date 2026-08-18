package com.vendex.gateway.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vendex.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class GatewayJwtValidator {

    private final AuthJwksProvider keys;
    private final GatewayProperties properties;
    private final Clock clock;

    public GatewayJwtValidator(AuthJwksProvider keys, GatewayProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    public GatewayPrincipal validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new InvalidAccessTokenException();
            }
            JOSEObjectType type = jwt.getHeader().getType();
            if (type != null && !JOSEObjectType.JWT.equals(type)) {
                throw new InvalidAccessTokenException();
            }
            String kid = jwt.getHeader().getKeyID();
            if (kid == null || kid.isBlank() || !jwt.verify(new RSASSAVerifier(keys.keyFor(kid)))) {
                throw new InvalidAccessTokenException();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            Instant skewedPast = now.minus(properties.jwt().clockSkew());
            Instant skewedFuture = now.plus(properties.jwt().clockSkew());
            Date expiration = claims.getExpirationTime();
            Date issuedAt = claims.getIssueTime();
            Date notBefore = claims.getNotBeforeTime();
            if (!properties.jwt().issuer().equals(claims.getIssuer())
                    || expiration == null
                    || !expiration.toInstant().isAfter(skewedPast)
                    || issuedAt == null
                    || issuedAt.toInstant().isAfter(skewedFuture)
                    || (notBefore != null && notBefore.toInstant().isAfter(skewedFuture))) {
                throw new InvalidAccessTokenException();
            }

            UUID userId = UUID.fromString(claims.getSubject());
            GatewayRole role = GatewayRole.valueOf(claims.getStringClaim("role"));
            return new GatewayPrincipal(userId, role, expiration.toInstant());
        } catch (InvalidAccessTokenException | JwksUnavailableException error) {
            throw error;
        } catch (Exception error) {
            throw new InvalidAccessTokenException();
        }
    }
}
