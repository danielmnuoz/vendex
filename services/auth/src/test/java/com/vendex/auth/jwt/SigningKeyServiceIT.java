package com.vendex.auth.jwt;

import com.vendex.auth.repository.SigningKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class SigningKeyServiceIT {

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

    @Autowired
    SigningKeyService signingKeys;

    @Autowired
    SigningKeyRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentCallersRotateAnObservedKeyExactlyOnce() throws Exception {
        UUID expectedKid = repository.findActive().orElseThrow().id();
        int beforeCount = keyCount();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<SigningKeyService.ActivationResult>> futures = List.of(
                    pool.submit(() -> rotateAfterSignal(expectedKid, ready, start)),
                    pool.submit(() -> rotateAfterSignal(expectedKid, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<SigningKeyService.ActivationResult> results = List.of(
                    futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));

            assertThat(results)
                    .extracting(SigningKeyService.ActivationResult::activation)
                    .containsExactlyInAnyOrder(
                            SigningKeyService.Activation.ROTATED,
                            SigningKeyService.Activation.UNCHANGED);
            UUID replacementKid = repository.findActive().orElseThrow().id();
            assertThat(replacementKid).isNotEqualTo(expectedKid);
            assertThat(results)
                    .extracting(result -> result.activeKey().id())
                    .containsOnly(replacementKid);
            assertThat(keyCount()).isEqualTo(beforeCount + 1);
            assertThat(activeKeyCount()).isOne();
            assertThat(repository.findById(expectedKid).orElseThrow())
                    .satisfies(old -> {
                        assertThat(old.rotatedAt()).isNotNull();
                        assertThat(old.revokedAt()).isNull();
                    });
            assertThat(repository.findAllPublished())
                    .extracting(key -> key.id())
                    .contains(expectedKid, replacementKid);
            assertThat(signingKeys.getActiveSigner().kid()).isEqualTo(replacementKid);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedReplacementInsertRollsBackTheOldKeysRotation() {
        UUID expectedKid = repository.findActive().orElseThrow().id();
        int beforeCount = keyCount();
        jdbc.execute("""
                ALTER TABLE signing_keys
                ADD CONSTRAINT test_reject_replacement
                CHECK (id = '%s'::uuid) NOT VALID
                """.formatted(expectedKid));

        try {
            assertThatThrownBy(() -> signingKeys.rotateSigningKey(expectedKid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("failed to rotate signing key");

            assertThat(repository.findActive().orElseThrow().id()).isEqualTo(expectedKid);
            assertThat(repository.findById(expectedKid).orElseThrow().rotatedAt()).isNull();
            assertThat(activeKeyCount()).isOne();
            assertThat(keyCount()).isEqualTo(beforeCount);
        } finally {
            jdbc.execute("ALTER TABLE signing_keys DROP CONSTRAINT test_reject_replacement");
        }
    }

    private SigningKeyService.ActivationResult rotateAfterSignal(
            UUID expectedKid,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return signingKeys.rotateSigningKey(expectedKid);
    }

    private int activeKeyCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM signing_keys
                WHERE rotated_at IS NULL AND revoked_at IS NULL
                """, Integer.class);
    }

    private int keyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM signing_keys", Integer.class);
    }
}
