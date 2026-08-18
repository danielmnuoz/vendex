package com.vendex.gateway.config;

import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.buylist.v1.BuyListServiceGrpc;
import com.vendex.event.v1.EventServiceGrpc;
import com.vendex.inventory.v1.InventoryServiceGrpc;
import com.vendex.notification.v1.NotificationServiceGrpc;
import com.vendex.overlap.v1.OverlapServiceGrpc;
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

    @Bean(name = "eventChannel", destroyMethod = "shutdownNow")
    ManagedChannel eventChannel(GatewayProperties properties) {
        return channel(properties.services().eventTarget(), properties.services().plaintext());
    }

    @Bean
    EventServiceGrpc.EventServiceBlockingStub eventStub(
            @Qualifier("eventChannel") ManagedChannel eventChannel) {
        return EventServiceGrpc.newBlockingStub(eventChannel);
    }

    @Bean(name = "inventoryChannel", destroyMethod = "shutdownNow")
    ManagedChannel inventoryChannel(GatewayProperties properties) {
        return channel(properties.services().inventoryTarget(), properties.services().plaintext());
    }

    @Bean
    InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub(
            @Qualifier("inventoryChannel") ManagedChannel inventoryChannel) {
        return InventoryServiceGrpc.newBlockingStub(inventoryChannel);
    }

    @Bean(name = "buyListChannel", destroyMethod = "shutdownNow")
    ManagedChannel buyListChannel(GatewayProperties properties) {
        return channel(properties.services().buyListTarget(), properties.services().plaintext());
    }

    @Bean
    BuyListServiceGrpc.BuyListServiceBlockingStub buyListStub(
            @Qualifier("buyListChannel") ManagedChannel buyListChannel) {
        return BuyListServiceGrpc.newBlockingStub(buyListChannel);
    }

    @Bean(name = "overlapChannel", destroyMethod = "shutdownNow")
    ManagedChannel overlapChannel(GatewayProperties properties) {
        return channel(properties.services().overlapTarget(), properties.services().plaintext());
    }

    @Bean
    OverlapServiceGrpc.OverlapServiceBlockingStub overlapStub(
            @Qualifier("overlapChannel") ManagedChannel overlapChannel) {
        return OverlapServiceGrpc.newBlockingStub(overlapChannel);
    }

    @Bean(name = "notificationChannel", destroyMethod = "shutdownNow")
    ManagedChannel notificationChannel(GatewayProperties properties) {
        return channel(properties.services().notificationTarget(), properties.services().plaintext());
    }

    @Bean
    NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub(
            @Qualifier("notificationChannel") ManagedChannel notificationChannel) {
        return NotificationServiceGrpc.newBlockingStub(notificationChannel);
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
