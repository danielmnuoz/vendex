package com.vendex.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: boots the full Spring context against a real (Testcontainers)
 * Postgres. Verifies that wiring, Flyway migrations, the signing-key
 * bootstrap PostConstruct, and the gRPC server registration all succeed.
 */
@SpringBootTest
@Testcontainers
class AuthApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        // 32-byte base64-encoded test key; never used outside tests.
        registry.add("auth.crypto.encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // Lower bcrypt cost in tests so they finish in seconds, not minutes.
        registry.add("auth.bcrypt.cost", () -> "4");
        // Bind gRPC to a random port so parallel test classes don't collide.
        registry.add("grpc.server.port", () -> "0");
    }

    @Test
    void contextLoads() {
        // Pass if Spring boots, Flyway migrates, and SigningKeyService
        // successfully generates the initial signing key.
    }
}
