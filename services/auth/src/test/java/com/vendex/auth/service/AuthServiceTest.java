package com.vendex.auth.service;

import com.vendex.auth.config.AuthProperties;
import com.vendex.auth.domain.RefreshToken;
import com.vendex.auth.domain.Role;
import com.vendex.auth.domain.User;
import com.vendex.auth.jwt.JwtService;
import com.vendex.auth.repository.RefreshTokenRepository;
import com.vendex.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock JwtService jwtService;

    BCryptPasswordEncoder encoder;
    AuthProperties props;
    Clock fixedClock;
    AuthService svc;

    @BeforeEach
    void setUp() {
        // Cost 4 keeps the suite fast — security tests prove the cost-12
        // property; this test is about logic.
        encoder = new BCryptPasswordEncoder(4);
        props = new AuthProperties(
                new AuthProperties.Jwt("https://test", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AuthProperties.Bcrypt(4),
                new AuthProperties.Crypto("")
        );
        fixedClock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);
        svc = new AuthService(users, refreshTokens, jwtService, encoder, props, fixedClock);
        svc.initDummyHash();
    }

    @Test
    void register_rejects_duplicate_email() {
        when(users.findByEmailIgnoreCase("a@b.com"))
                .thenReturn(Optional.of(sampleUser("a@b.com")));

        assertThatThrownBy(() -> svc.register("a@b.com", "supersecret", Role.VENDOR, "Shop", "Austin", "TX"))
                .isInstanceOf(AuthExceptions.EmailAlreadyRegisteredException.class);
    }

    @Test
    void register_requires_shop_name_for_vendors() {
        assertThatThrownBy(() -> svc.register("a@b.com", "supersecret", Role.VENDOR, null, "Austin", "TX"))
                .isInstanceOf(AuthExceptions.ValidationException.class)
                .hasMessageContaining("shop_name");
    }

    @Test
    void register_validates_email_and_password() {
        assertThatThrownBy(() -> svc.register("not-an-email", "supersecret", Role.ATTENDEE, null, null, null))
                .isInstanceOf(AuthExceptions.ValidationException.class);
        assertThatThrownBy(() -> svc.register("a@b.com", "short", Role.ATTENDEE, null, null, null))
                .isInstanceOf(AuthExceptions.ValidationException.class);
    }

    @Test
    void login_rejects_unknown_email_with_generic_error() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.login("missing@example.com", "whatever-password"))
                .isInstanceOf(AuthExceptions.InvalidCredentialsException.class);
    }

    @Test
    void login_rejects_wrong_password_with_generic_error() {
        User existing = sampleUserWithPassword("a@b.com", "rightpassword");
        when(users.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> svc.login("a@b.com", "wrongpassword"))
                .isInstanceOf(AuthExceptions.InvalidCredentialsException.class);
    }

    @Test
    void login_issues_token_pair_on_success() {
        User existing = sampleUserWithPassword("a@b.com", "rightpassword");
        when(users.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(existing));
        when(jwtService.issue(eq(existing.id()), eq(Role.VENDOR)))
                .thenReturn(new JwtService.Issued("access.token.value", fixedClock.instant().plus(Duration.ofMinutes(15))));
        when(refreshTokens.insert(eq(existing.id()), anyString(), any()))
                .thenAnswer(inv -> new RefreshToken(
                        UUID.randomUUID(),
                        existing.id(),
                        inv.getArgument(1, String.class),
                        inv.getArgument(2, Instant.class),
                        false,
                        fixedClock.instant()
                ));

        AuthService.LoginResult r = svc.login("a@b.com", "rightpassword");

        assertThat(r.accessToken()).isEqualTo("access.token.value");
        assertThat(r.refreshToken()).isNotBlank();
        verify(refreshTokens, times(1)).insert(eq(existing.id()), anyString(), any());
    }

    @Test
    void refresh_throws_when_consume_returns_empty() {
        when(refreshTokens.consume(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.refresh("some-refresh-token"))
                .isInstanceOf(AuthExceptions.InvalidTokenException.class);
    }

    @Test
    void refresh_throws_when_token_expired() {
        UUID userId = UUID.randomUUID();
        when(refreshTokens.consume(anyString())).thenReturn(Optional.of(new RefreshToken(
                UUID.randomUUID(),
                userId,
                "hash",
                fixedClock.instant().minus(Duration.ofMinutes(1)),  // expired
                false,
                fixedClock.instant().minus(Duration.ofDays(8))
        )));

        assertThatThrownBy(() -> svc.refresh("some-refresh-token"))
                .isInstanceOf(AuthExceptions.InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    private User sampleUser(String email) {
        return new User(
                UUID.randomUUID(), email, encoder.encode("placeholder"),
                Role.VENDOR, "Shop", "Austin", "TX",
                fixedClock.instant(), fixedClock.instant()
        );
    }

    private User sampleUserWithPassword(String email, String password) {
        return new User(
                UUID.randomUUID(), email, encoder.encode(password),
                Role.VENDOR, "Shop", "Austin", "TX",
                fixedClock.instant(), fixedClock.instant()
        );
    }
}
