package com.vendex.auth.service;

import com.vendex.auth.domain.Role;
import com.vendex.auth.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end persistence test for the register → login → refresh flow against
 * a real (Testcontainers) Postgres.
 *
 * <p>This is the guard for the assigned-{@code @Id} bug: {@code AuthService}'s
 * unit tests mock the repository, so they can't catch Spring Data JDBC issuing
 * an UPDATE instead of an INSERT for a new user. Only a test that actually
 * round-trips through Postgres proves {@code register()} persists a row that
 * {@code login()} can then find.
 */
@SpringBootTest
@Testcontainers
class AuthServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        // 32-byte base64-encoded test key; never used outside tests.
        registry.add("auth.crypto.encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // Lower bcrypt cost so the suite finishes in seconds, not minutes.
        registry.add("auth.bcrypt.cost", () -> "4");
        registry.add("grpc.server.port", () -> "0");
    }

    @Autowired AuthService auth;

    @Test
    void register_persists_a_user_that_login_can_authenticate() {
        String email = "vendor+" + UUID.randomUUID() + "@example.com";

        UUID userId = auth.register(email, "supersecret", Role.VENDOR, "Shop", "Austin", "TX");
        assertThat(userId).isNotNull();

        AuthService.LoginResult result = auth.login(email, "supersecret");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        // The issued access token validates and carries the registered identity.
        Optional<JwtService.Claims> claims = auth.validateAccessToken(result.accessToken());
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().role()).isEqualTo(Role.VENDOR);
    }

    @Test
    void login_rejects_wrong_password_for_a_registered_user() {
        String email = "attendee+" + UUID.randomUUID() + "@example.com";
        auth.register(email, "rightpassword", Role.ATTENDEE, null, "Dallas", "TX");

        assertThatThrownBy(() -> auth.login(email, "wrongpassword"))
                .isInstanceOf(AuthExceptions.InvalidCredentialsException.class);
    }

    @Test
    void register_rejects_a_duplicate_email() {
        String email = "dupe+" + UUID.randomUUID() + "@example.com";
        auth.register(email, "supersecret", Role.ATTENDEE, null, null, null);

        assertThatThrownBy(() -> auth.register(email, "supersecret", Role.ATTENDEE, null, null, null))
                .isInstanceOf(AuthExceptions.EmailAlreadyRegisteredException.class);
    }

    @Test
    void refresh_rotates_and_issues_a_new_token_pair() {
        String email = "refresh+" + UUID.randomUUID() + "@example.com";
        auth.register(email, "supersecret", Role.VENDOR, "Shop", "Austin", "TX");
        AuthService.LoginResult first = auth.login(email, "supersecret");

        AuthService.LoginResult rotated = auth.refresh(first.refreshToken());
        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());

        // The old refresh token is single-use: replaying it must fail.
        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(AuthExceptions.InvalidTokenException.class);
    }
}
