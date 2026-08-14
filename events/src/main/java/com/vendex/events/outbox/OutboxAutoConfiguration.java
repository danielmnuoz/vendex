package com.vendex.events.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the transactional outbox into any service on the classpath that has a
 * datasource. A service gets the full mechanism just by depending on the
 * {@code events} module — no per-service boilerplate.
 *
 * <p>Ordered after the datasource/jdbc, Kafka, and Jackson auto-configs so the
 * beans we depend on ({@link NamedParameterJdbcTemplate}, {@link KafkaTemplate},
 * {@link ObjectMapper}) already exist. The {@link OutboxRelay} is additionally
 * gated on a {@code KafkaTemplate} being present, so a service can wire only the
 * writer (e.g. in a test slice) without a broker.
 */
@AutoConfiguration(after = {
        JdbcTemplateAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(prefix = "vendex.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxAutoConfiguration {

    /**
     * Non-web services do not pull in Spring's Jackson builder, so
     * {@link JacksonAutoConfiguration} may not create an {@link ObjectMapper}
     * even though jackson-databind is present. Supply a lean fallback that
     * discovers Java-time support while allowing applications to override it.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper outboxObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRepository outboxRepository(NamedParameterJdbcTemplate jdbc) {
        return new OutboxRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(OutboxRepository repository, ObjectMapper objectMapper) {
        return new OutboxWriter(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxRelay outboxRelay(OutboxRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   OutboxProperties properties) {
        return new OutboxRelay(repository, kafkaTemplate, properties);
    }
}
