package com.vendex.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vendex.auth.config.AuthProperties;
import com.vendex.auth.domain.Role;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SigningKeyService signingKeys;
    private final AuthProperties props;
    private final Clock clock;

    public JwtService(SigningKeyService signingKeys, AuthProperties props, Clock clock) {
        this.signingKeys = signingKeys;
        this.props = props;
        this.clock = clock;
    }

    /** Signs an access token for the given user. */
    public Issued issue(UUID userId, Role role) {
        var signer = signingKeys.getActiveSigner();
        Instant now = clock.instant();
        Instant exp = now.plus(props.jwt().accessTokenTtl());

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signer.kid().toString())
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(props.jwt().issuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("role", role.name())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signer.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign access token", e);
        }
        return new Issued(jwt.serialize(), exp);
    }

    /**
     * Validates an access token. Returns the parsed claims if the signature
     * matches a known kid, the issuer is ours, and the token has not
     * expired. Returns empty otherwise.
     */
    public Optional<Claims> validate(String token) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            return Optional.empty();
        }

        String kidString = jwt.getHeader().getKeyID();
        if (kidString == null || kidString.isBlank()) {
            return Optional.empty();
        }
        UUID kid;
        try {
            kid = UUID.fromString(kidString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        var publicKeyOpt = signingKeys.findPublicKeyByKid(kid);
        if (publicKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            if (!jwt.verify(new RSASSAVerifier(publicKeyOpt.get()))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!props.jwt().issuer().equals(claims.getIssuer())) {
                return Optional.empty();
            }
            Instant exp = claims.getExpirationTime().toInstant();
            if (!exp.isAfter(clock.instant())) {
                return Optional.empty();
            }
            String roleStr = claims.getStringClaim("role");
            if (roleStr == null) return Optional.empty();
            Role role;
            try {
                role = Role.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            UUID subject = UUID.fromString(claims.getSubject());
            return Optional.of(new Claims(subject, role, exp));
        } catch (JOSEException | ParseException e) {
            return Optional.empty();
        }
    }

    public record Issued(String token, Instant expiresAt) {}

    public record Claims(UUID userId, Role role, Instant expiresAt) {}
}
