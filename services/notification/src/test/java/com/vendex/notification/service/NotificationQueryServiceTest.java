package com.vendex.notification.service;

import com.vendex.notification.domain.DigestMode;
import com.vendex.notification.domain.NotificationPreferences;
import com.vendex.notification.domain.NotificationRecord;
import com.vendex.notification.domain.NotificationTrigger;
import com.vendex.notification.domain.OverlapProjection;
import com.vendex.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Mock NotificationRepository repository;
    @Mock NotificationProjectionService projections;

    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(
                repository, projections, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void preferencePatchPreservesOmittedFieldsAndReplacesEventMutes() {
        UUID userId = UUID.randomUUID();
        UUID oldMute = UUID.randomUUID();
        UUID newMute = UUID.randomUUID();
        NotificationPreferences current = new NotificationPreferences(
                userId, true, false, true, false, true,
                DigestMode.REAL_TIME, Set.of(oldMute), NOW.minusSeconds(1));
        when(repository.getOrCreatePreferences(userId, NOW)).thenReturn(current);
        when(repository.savePreferences(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId,
                new NotificationQueryService.PreferencesPatch(
                        false, true, null, null, null,
                        DigestMode.DAILY, Set.of(newMute), true));

        assertThat(result.inAppEnabled()).isFalse();
        assertThat(result.emailEnabled()).isTrue();
        assertThat(result.overlapBuyListEnabled()).isTrue();
        assertThat(result.overlapLiquidateEnabled()).isFalse();
        assertThat(result.savedOverlapActiveEnabled()).isTrue();
        assertThat(result.digestMode()).isEqualTo(DigestMode.DAILY);
        assertThat(result.mutedEventIds()).containsExactly(newMute);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        verify(projections).rescheduleVendor(userId);
    }

    @Test
    void notificationPaginationUsesOneExtraRowWithoutLeakingIt() {
        UUID vendorId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(repository.getOrCreatePreferences(vendorId, NOW))
                .thenReturn(preferences(vendorId));
        when(repository.listVisible(vendorId, eventId, NOW, 3, 0))
                .thenReturn(List.of(notification(vendorId, eventId),
                        notification(vendorId, eventId), notification(vendorId, eventId)));

        NotificationQueryService.NotificationPage page =
                service.getNotifications(vendorId, eventId, 2, 0);

        assertThat(page.notifications()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPageOffset()).isEqualTo(2);
    }

    @Test
    void onlyTheOverlapSellerCanReadItsInterestQueue() {
        UUID overlapId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(repository.findOverlap(overlapId)).thenReturn(Optional.of(
                new OverlapProjection(overlapId, UUID.randomUUID(), UUID.randomUUID(),
                        sellerId, UUID.randomUUID(), "normal", new BigDecimal("80"),
                        "{}", true, 1, NOW)));

        assertThatThrownBy(() -> service.listInterests(
                UUID.randomUUID(), overlapId, 25, 0))
                .isInstanceOf(NotificationExceptions.OwnershipException.class);
    }

    @Test
    void negativePageOffsetIsRejectedBeforeQueryingStorage() {
        UUID vendorId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getNotifications(vendorId, null, 25, -1))
                .isInstanceOf(NotificationExceptions.ValidationException.class)
                .hasMessage("page_offset must not be negative");
    }

    private static NotificationPreferences preferences(UUID vendorId) {
        return new NotificationPreferences(vendorId, true, false, true, true, true,
                DigestMode.REAL_TIME, Set.of(), NOW);
    }

    private static NotificationRecord notification(UUID vendorId, UUID eventId) {
        return new NotificationRecord(UUID.randomUUID(), vendorId, eventId,
                NotificationTrigger.OVERLAP_BUYLIST, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{}", false, true, NOW, NOW, NOW, NOW);
    }
}
