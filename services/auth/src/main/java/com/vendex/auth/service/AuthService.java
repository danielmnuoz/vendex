package com.vendex.auth.service;

import com.vendex.auth.config.AuthProperties;
import com.vendex.auth.domain.RefreshToken;
import com.vendex.auth.domain.Role;
import com.vendex.auth.domain.User;
import com.vendex.auth.jwt.JwtService;
import com.vendex.auth.repository.RefreshTokenRepository;
import com.vendex.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for identity and tokens. Stateless beyond its injected
 * collaborators; one instance per JVM, shared across all gRPC threads.
 *
 * <p>Notable security properties:
 * <ul>
 *   <li>Login always pays the bcrypt cost, even for unknown emails — see
 *       {@link #dummyHash} for why.</li>
 *   <li>Refresh-token rotation goes through {@link RefreshTokenRepository#consume},
 *       a compare-and-swap UPDATE that resolves concurrent refreshes to
 *       exactly one winner.</li>
 *   <li>Refresh tokens are stored as SHA-256 hashes; DB compromise leaks
 *       only hashes, not usable tokens.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthProperties props;
    private final Clock clock;

    /**
     * Generated at construction at the configured bcrypt cost. {@link #login}
     * compares against this when the email is unknown so timing of the
     * "unknown email" path matches the "wrong password" path. Hardcoding a
     * fixed-cost dummy would leak the configured cost via a timing oracle —
     * generating at startup avoids that.
     */
    private String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            JwtService jwtService,
            BCryptPasswordEncoder passwordEncoder,
            AuthProperties props,
            Clock clock
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-safety");
    }

    public UUID register(String email, String password, Role role, String shopName, String city, String state) {
        validateEmail(email);
        validatePassword(password);
        if (role == Role.VENDOR && (shopName == null || shopName.isBlank())) {
            throw new AuthExceptions.ValidationException("vendors must provide shop_name");
        }

        String normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new AuthExceptions.EmailAlreadyRegisteredException(normalized);
        }

        String passwordHash = passwordEncoder.encode(password);
        Instant now = clock.instant();
        User u = new User(
                UUID.randomUUID(),
                normalized,
                passwordHash,
                role,
                role == Role.VENDOR ? shopName : null,
                city,
                state,
                now,
                now
        );
        User saved = users.save(u);
        return saved.id();
    }

    public LoginResult login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            // Still pay the bcrypt cost so callers can't distinguish "missing
            // field" from "wrong credentials" via timing.
            passwordEncoder.matches("placeholder", dummyHash);
            throw new AuthExceptions.InvalidCredentialsException();
        }

        Optional<User> userOpt = users.findByEmailIgnoreCase(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            // Run bcrypt against the dummy hash so the email-unknown path
            // takes the same wall-clock time as the wrong-password path.
            passwordEncoder.matches(password, dummyHash);
            throw new AuthExceptions.InvalidCredentialsException();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthExceptions.InvalidCredentialsException();
        }

        return issueTokenPair(user);
    }

    public LoginResult refresh(String refreshTokenPlaintext) {
        if (refreshTokenPlaintext == null || refreshTokenPlaintext.isBlank()) {
            throw new AuthExceptions.InvalidTokenException("missing refresh token");
        }
        String hash = sha256Base64(refreshTokenPlaintext);
        RefreshToken consumed = refreshTokens.consume(hash)
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("refresh token not found or already used"));

        if (!consumed.expiresAt().isAfter(clock.instant())) {
            throw new AuthExceptions.InvalidTokenException("refresh token expired");
        }

        User user = users.findById(consumed.userId())
                .orElseThrow(() -> new AuthExceptions.UserNotFoundException("user " + consumed.userId() + " missing"));

        return issueTokenPair(user);
    }

    public Optional<JwtService.Claims> validateAccessToken(String token) {
        return jwtService.validate(token);
    }

    public User getProfile(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AuthExceptions.UserNotFoundException("user " + userId + " not found"));
    }

    public User updateProfile(UUID userId, String shopName, String city, String state) {
        User current = getProfile(userId);
        User updated = new User(
                current.id(),
                current.email(),
                current.passwordHash(),
                current.role(),
                shopName == null || shopName.isBlank() ? current.shopName() : shopName,
                city == null || city.isBlank() ? current.city() : city,
                state == null || state.isBlank() ? current.state() : state,
                current.createdAt(),
                clock.instant()
        );
        return users.save(updated);
    }

    private LoginResult issueTokenPair(User user) {
        JwtService.Issued access = jwtService.issue(user.id(), user.role());
        String refreshPlaintext = randomToken();
        String refreshHash = sha256Base64(refreshPlaintext);
        Instant refreshExpires = clock.instant().plus(props.jwt().refreshTokenTtl());
        refreshTokens.insert(user.id(), refreshHash, refreshExpires);
        return new LoginResult(access.token(), refreshPlaintext, access.expiresAt());
    }

    private void validateEmail(String email) {
        if (email == null || !email.contains("@") || email.length() > 254) {
            throw new AuthExceptions.ValidationException("invalid email");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new AuthExceptions.ValidationException("password must be at least 8 characters");
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record LoginResult(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}
}
