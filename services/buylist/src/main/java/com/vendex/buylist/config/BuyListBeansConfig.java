package com.vendex.buylist.config;

import com.vendex.cards.v1.CardCatalogServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyListProperties.class)
public class BuyListBeansConfig {

    @Bean
    Clock buyListClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel buyListCardCatalogChannel(BuyListProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.cardCatalogTarget())
                .usePlaintext()
                .build();
    }

    @Bean
    CardCatalogServiceGrpc.CardCatalogServiceBlockingStub buyListCardCatalogStub(
            ManagedChannel buyListCardCatalogChannel) {
        return CardCatalogServiceGrpc.newBlockingStub(buyListCardCatalogChannel);
    }
}
