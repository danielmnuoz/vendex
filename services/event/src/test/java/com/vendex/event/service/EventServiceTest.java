package com.vendex.event.service;

import com.vendex.event.domain.Event;
import com.vendex.event.domain.EventRegistration;
import com.vendex.event.domain.RegistrationRole;
import com.vendex.event.repository.EventRepository;
import com.vendex.events.contract.EventAttendeeRegistered;
import com.vendex.events.contract.EventCreated;
import com.vendex.events.contract.EventParticipantUnregistered;
import com.vendex.events.contract.EventUpdated;
import com.vendex.events.contract.EventVendorRegistered;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock EventRepository repository;
    @Mock OutboxWriter outbox;

    private EventService service() {
        return new EventService(repository, outbox, CLOCK);
    }

    @Test
    void createPersistsEventAndWritesCreatedEventToOutbox() {
        UUID organizerId = UUID.randomUUID();
        Event stored = event(organizerId);
        when(repository.insertEvent(eq(organizerId), eq("Collect-A-Con Dallas"), eq("Dallas"),
                eq("TX"), eq("Convention Center"), any(), any(), eq("Pokemon weekend"), eq(NOW)))
                .thenReturn(stored);

        Event result = service().create(organizerId, " Collect-A-Con Dallas ", "Dallas", "TX",
                "Convention Center", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4),
                "Pokemon weekend");

        assertThat(result).isEqualTo(stored);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("event"), eq(stored.id().toString()), eq(Topics.EVENT_CREATED),
                eq(stored.id().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(EventCreated.class);
    }

    @Test
    void vendorRegistrationWritesVendorEventKeyedByVendor() {
        Event event = event(UUID.randomUUID());
        UUID vendorId = UUID.randomUUID();
        EventRegistration registration = new EventRegistration(
                UUID.randomUUID(), event.id(), vendorId, RegistrationRole.VENDOR, "B-7", NOW);
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));
        when(repository.insertRegistration(event.id(), vendorId, RegistrationRole.VENDOR, "B-7", NOW))
                .thenReturn(registration);

        EventRegistration result = service().register(event.id(), vendorId, RegistrationRole.VENDOR, " B-7 ");

        assertThat(result).isEqualTo(registration);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("event_registration"), eq(registration.id().toString()),
                eq(Topics.EVENT_VENDOR_REGISTERED), eq(vendorId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(EventVendorRegistered.class);
    }

    @Test
    void attendeeRegistrationWritesAttendeeEvent() {
        Event event = event(UUID.randomUUID());
        UUID attendeeId = UUID.randomUUID();
        EventRegistration registration = new EventRegistration(
                UUID.randomUUID(), event.id(), attendeeId, RegistrationRole.ATTENDEE, null, NOW);
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));
        when(repository.insertRegistration(event.id(), attendeeId, RegistrationRole.ATTENDEE, null, NOW))
                .thenReturn(registration);

        service().register(event.id(), attendeeId, RegistrationRole.ATTENDEE, "");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("event_registration"), eq(registration.id().toString()),
                eq(Topics.EVENT_ATTENDEE_REGISTERED), eq(attendeeId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(EventAttendeeRegistered.class);
    }

    @Test
    void attendeeCannotClaimBooth() {
        Event event = event(UUID.randomUUID());
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().register(
                event.id(), UUID.randomUUID(), RegistrationRole.ATTENDEE, "A-1"))
                .isInstanceOf(EventExceptions.ValidationException.class)
                .hasMessageContaining("attendees cannot have a booth");

        verify(repository, never()).insertRegistration(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateRegistrationBecomesDomainConflict() {
        Event event = event(UUID.randomUUID());
        UUID vendorId = UUID.randomUUID();
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));
        when(repository.insertRegistration(event.id(), vendorId, RegistrationRole.VENDOR, null, NOW))
                .thenThrow(new DuplicateKeyException("uq_event_registration"));

        assertThatThrownBy(() -> service().register(
                event.id(), vendorId, RegistrationRole.VENDOR, null))
                .isInstanceOf(EventExceptions.AlreadyRegisteredException.class);

        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void onlyOrganizerMayUpdateEvent() {
        Event event = event(UUID.randomUUID());
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().update(
                event.id(), UUID.randomUUID(), event.name(), event.city(), event.state(),
                event.venue(), event.startDate(), event.endDate(), event.description()))
                .isInstanceOf(EventExceptions.OrganizerMismatchException.class);
    }

    @Test
    void organizerUpdatePublishesUpdatedEvent() {
        Event current = event(UUID.randomUUID());
        Event updated = new Event(
                current.id(), current.organizerId(), "New name", current.city(), current.state(),
                current.venue(), current.startDate(), current.endDate(), current.description(),
                current.createdAt(), NOW);
        when(repository.findEvent(current.id())).thenReturn(Optional.of(current));
        when(repository.updateEvent(eq(current.id()), eq("New name"), eq(current.city()),
                eq(current.state()), eq(current.venue()), eq(current.startDate()),
                eq(current.endDate()), eq(current.description()), eq(NOW)))
                .thenReturn(Optional.of(updated));

        Event result = service().update(
                current.id(), current.organizerId(), "New name", current.city(), current.state(),
                current.venue(), current.startDate(), current.endDate(), current.description());

        assertThat(result).isEqualTo(updated);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("event"), eq(current.id().toString()), eq(Topics.EVENT_UPDATED),
                eq(current.id().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(EventUpdated.class);
    }

    @Test
    void unregisterPublishesRosterRemovalWhenRegistrationExists() {
        Event event = event(UUID.randomUUID());
        UUID vendorId = UUID.randomUUID();
        EventRegistration registration = new EventRegistration(
                UUID.randomUUID(), event.id(), vendorId, RegistrationRole.VENDOR, "B-7", NOW);
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));
        when(repository.deleteRegistration(event.id(), vendorId)).thenReturn(Optional.of(registration));

        service().unregister(event.id(), vendorId);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("event_registration"), eq(registration.id().toString()),
                eq(Topics.EVENT_PARTICIPANT_UNREGISTERED), eq(vendorId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(EventParticipantUnregistered.class);
    }

    @Test
    void unregisterIsIdempotentWhenRegistrationIsAlreadyAbsent() {
        Event event = event(UUID.randomUUID());
        UUID attendeeId = UUID.randomUUID();
        when(repository.findEvent(event.id())).thenReturn(Optional.of(event));
        when(repository.deleteRegistration(event.id(), attendeeId)).thenReturn(Optional.empty());

        service().unregister(event.id(), attendeeId);

        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void listUsesOneExtraRowToSignalNextPage() {
        Event first = event(UUID.randomUUID());
        Event second = event(UUID.randomUUID());
        Event third = event(UUID.randomUUID());
        when(repository.listEvents(3, 10)).thenReturn(List.of(first, second, third));

        EventService.Page page = service().list(2, 10);

        assertThat(page.events()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextPageOffset()).isEqualTo(12);
    }

    @Test
    void rejectsBackwardsDateRangeBeforeWriting() {
        assertThatThrownBy(() -> service().create(
                UUID.randomUUID(), "Show", "Dallas", "TX", null,
                LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 3), null))
                .isInstanceOf(EventExceptions.ValidationException.class)
                .hasMessageContaining("end_date");

        verify(repository, never()).insertEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsVenueLongerThanDatabaseColumn() {
        assertThatThrownBy(() -> service().create(
                UUID.randomUUID(), "Show", "Dallas", "TX", "x".repeat(201),
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), null))
                .isInstanceOf(EventExceptions.ValidationException.class)
                .hasMessageContaining("venue");

        verify(repository, never()).insertEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Event event(UUID organizerId) {
        return new Event(
                UUID.randomUUID(), organizerId, "Collect-A-Con Dallas", "Dallas", "TX",
                "Convention Center", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4),
                "Pokemon weekend", NOW, NOW);
    }
}
