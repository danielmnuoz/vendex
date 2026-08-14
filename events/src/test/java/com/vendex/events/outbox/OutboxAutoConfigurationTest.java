package com.vendex.events.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
            .withUserConfiguration(JdbcConfiguration.class);

    @Test
    void suppliesJavaTimeMapperAndWriterForNonWebServices() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(OutboxWriter.class);

            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(Instant.parse("2026-08-14T12:00:00Z")))
                    .isEqualTo("\"2026-08-14T12:00:00Z\"");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfiguration {
        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }
    }
}
