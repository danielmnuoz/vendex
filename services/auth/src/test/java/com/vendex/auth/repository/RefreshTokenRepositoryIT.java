package com.vendex.auth.repository;

import com.vendex.auth.domain.RefreshToken;
import com.vendex.auth.domain.Role;
import com.vendex.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the refresh-token compare-and-swap actually serializes concurrent
 * consumers. 16 threads race on the same token; exactly one must observe
 * the row, every other must observe empty.
 *
 * <p>This is the load-bearing security test for refresh-token rotation
 * safety — if {@link RefreshTokenRepository#consume} ever loses its CAS
 * semantics, two clients could both succeed in refreshing the same token
 * and we'd have a duplicate-token vulnerability.
 */
@SpringBootTest
@Testcontainers
class RefreshTokenRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        registry.add("auth.crypto.encryption-key",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("auth.bcrypt.cost", () -> "4");
        registry.add("grpc.server.port", () -> "0");
    }

    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;

    @Test
    void exactly_one_consumer_wins_under_concurrency() throws Exception {
        // FK constraint requires the user to exist. id is null so Spring Data
        // JDBC INSERTs and Postgres assigns the UUID; we read it back for the FK.
        User saved = users.save(new User(
                null,
                "race-test+" + UUID.randomUUID() + "@example.com",
                "placeholder-hash",
                Role.VENDOR, "Shop", "City", "ST",
                Instant.now(), Instant.now()
        ));
        UUID userId = saved.id();

        String hash = "race-token-hash";
        Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
        refreshTokens.insert(userId, hash, expires);

        int workers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<Optional<RefreshToken>>> tasks = IntStream.range(0, workers)
                    .<Callable<Optional<RefreshToken>>>mapToObj(i -> () -> refreshTokens.consume(hash))
                    .toList();

            List<Future<Optional<RefreshToken>>> results = pool.invokeAll(tasks);
            long winners = results.stream()
                    .filter(f -> {
                        try { return f.get(5, TimeUnit.SECONDS).isPresent(); }
                        catch (Exception e) { throw new AssertionError(e); }
                    })
                    .count();
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
