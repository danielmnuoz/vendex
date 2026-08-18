package com.vendex.gateway.config;

import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientsConfig {

    @Bean(name = "authChannel", destroyMethod = "shutdownNow")
    ManagedChannel authChannel(GatewayProperties properties) {
        return channel(properties.services().authTarget(), properties.services().plaintext());
    }

    @Bean
    AuthServiceGrpc.AuthServiceBlockingStub authStub(
            @Qualifier("authChannel") ManagedChannel authChannel) {
        return AuthServiceGrpc.newBlockingStub(authChannel);
    }

    @Bean(name = "cardCatalogChannel", destroyMethod = "shutdownNow")
    ManagedChannel cardCatalogChannel(GatewayProperties properties) {
        return channel(properties.services().cardCatalogTarget(), properties.services().plaintext());
    }

    @Bean
    CardCatalogServiceGrpc.CardCatalogServiceBlockingStub cardCatalogStub(
            @Qualifier("cardCatalogChannel") ManagedChannel cardCatalogChannel) {
        return CardCatalogServiceGrpc.newBlockingStub(cardCatalogChannel);
    }

    private static ManagedChannel channel(String target, boolean plaintext) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target)
                .maxInboundMessageSize(8 * 1024 * 1024);
        if (plaintext) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }
        return builder.build();
    }
}
