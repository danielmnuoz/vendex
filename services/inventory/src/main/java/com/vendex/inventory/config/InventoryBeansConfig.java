package com.vendex.inventory.config;

import com.vendex.cards.v1.CardCatalogServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryBeansConfig {

    @Bean
    Clock inventoryClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel cardCatalogChannel(InventoryProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.cardCatalogTarget())
                .usePlaintext()
                .build();
    }

    @Bean
    CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cardCatalogStub(ManagedChannel channel) {
        return CardCatalogServiceGrpc.newBlockingStub(channel);
    }
}
