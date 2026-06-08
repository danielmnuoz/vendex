package com.vendex.auth.config;

import com.vendex.auth.crypto.Aead;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.util.Base64;

/**
 * Wires the small set of plain Java beans this service needs. Kept as a
 * single class so a reader doesn't have to grep across multiple {@code
 * @Configuration} files to understand startup wiring.
 */
@Configuration
public class AuthBeansConfig {

    /**
     * The configured bcrypt cost is applied to every hash AND to a "dummy"
     * hash AuthService uses for timing-safe equality on unknown emails (see
     * AuthService for the why). Returning the same encoder from both call
     * sites guarantees they agree on cost.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder(AuthProperties props) {
        return new BCryptPasswordEncoder(props.bcrypt().cost());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Aead aead(AuthProperties props) {
        String b64 = props.crypto().encryptionKey();
        if (b64 == null || b64.isBlank()) {
            // Permitted in test profiles that don't generate signing keys.
            // SigningKeyService will surface the missing-key error at the
            // moment it actually tries to encrypt.
            return Aead.uninitialized();
        }
        byte[] key = Base64.getDecoder().decode(b64);
        return Aead.fromRawKey(key);
    }
}
