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
import com.vendex.events.contract.ParticipantRole;
import com.vendex.events.contract.Topics;
import com.vendex.events.outbox.OutboxWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final EventRepository repository;
    private final OutboxWriter outbox;
    private final Clock clock;

    public EventService(EventRepository repository, OutboxWriter outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Event create(UUID organizerId, String name, String city, String state,
                        String venue, LocalDate startDate, LocalDate endDate,
                        String description) {
        validateEvent(organizerId, name, city, state, venue, startDate, endDate);
        Instant now = clock.instant();
        Event event = repository.insertEvent(
                organizerId, clean(name), clean(city), clean(state), blankToNull(venue),
                startDate, endDate, blankToNull(description), now);
        outbox.write(
                "event",
                event.id().toString(),
                Topics.EVENT_CREATED,
                event.id().toString(),
                new EventCreated(event.id(), event.organizerId(), event.name(), event.city(),
                        event.state(), event.startDate(), event.endDate(), now)
        );
        return event;
    }

    @Transactional
    public Event update(UUID eventId, UUID organizerId, String name, String city,
                        String state, String venue, LocalDate startDate,
                        LocalDate endDate, String description) {
        requireId(eventId, "event_id");
        validateEvent(organizerId, name, city, state, venue, startDate, endDate);
        Event current = get(eventId);
        if (!current.organizerId().equals(organizerId)) {
            throw new EventExceptions.OrganizerMismatchException();
        }
        Event updated = repository.updateEvent(eventId, clean(name), clean(city), clean(state),
                        blankToNull(venue), startDate, endDate, blankToNull(description), clock.instant())
                .orElseThrow(EventExceptions.EventNotFoundException::new);
        outbox.write("event", updated.id().toString(), Topics.EVENT_UPDATED,
                updated.id().toString(),
                new EventUpdated(updated.id(), updated.organizerId(), updated.name(),
                        updated.city(), updated.state(), updated.startDate(), updated.endDate(),
                        updated.updatedAt()));
        return updated;
    }

    @Transactional
    public EventRegistration register(UUID eventId, UUID userId,
                                      RegistrationRole role, String booth) {
        requireId(eventId, "event_id");
        requireId(userId, "user_id");
        if (role == null) {
            throw new EventExceptions.ValidationException("registration role is required");
        }
        get(eventId);
        String normalizedBooth = blankToNull(booth);
        if (role == RegistrationRole.ATTENDEE && normalizedBooth != null) {
            throw new EventExceptions.ValidationException("attendees cannot have a booth");
        }
        if (normalizedBooth != null && normalizedBooth.length() > 40) {
            throw new EventExceptions.ValidationException("booth must be 40 characters or fewer");
        }

        Instant now = clock.instant();
        EventRegistration registration;
        try {
            registration = repository.insertRegistration(eventId, userId, role, normalizedBooth, now);
        } catch (DuplicateKeyException e) {
            throw new EventExceptions.AlreadyRegisteredException();
        }

        if (role == RegistrationRole.VENDOR) {
            outbox.write("event_registration", registration.id().toString(),
                    Topics.EVENT_VENDOR_REGISTERED, userId.toString(),
                    new EventVendorRegistered(eventId, userId, now));
        } else {
            outbox.write("event_registration", registration.id().toString(),
                    Topics.EVENT_ATTENDEE_REGISTERED, userId.toString(),
                    new EventAttendeeRegistered(eventId, userId, now));
        }
        return registration;
    }

    @Transactional
    public void unregister(UUID eventId, UUID userId) {
        requireId(eventId, "event_id");
        requireId(userId, "user_id");
        get(eventId);
        repository.deleteRegistration(eventId, userId).ifPresent(registration ->
                outbox.write("event_registration", registration.id().toString(),
                        Topics.EVENT_PARTICIPANT_UNREGISTERED, userId.toString(),
                        new EventParticipantUnregistered(eventId, userId,
                                registration.role() == RegistrationRole.VENDOR
                                        ? ParticipantRole.VENDOR
                                        : ParticipantRole.ATTENDEE,
                                clock.instant())));
    }

    public Event get(UUID eventId) {
        requireId(eventId, "event_id");
        return repository.findEvent(eventId)
                .orElseThrow(EventExceptions.EventNotFoundException::new);
    }

    public Page list(int requestedPageSize, int offset) {
        if (offset < 0) {
            throw new EventExceptions.ValidationException("page_offset must not be negative");
        }
        int pageSize = requestedPageSize <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(requestedPageSize, MAX_PAGE_SIZE);
        List<Event> rows = repository.listEvents(pageSize + 1, offset);
        boolean hasMore = rows.size() > pageSize;
        List<Event> events = hasMore
                ? new ArrayList<>(rows.subList(0, pageSize))
                : new ArrayList<>(rows);
        return new Page(List.copyOf(events), hasMore ? offset + pageSize : 0, hasMore);
    }

    public List<EventRegistration> listRegistrations(UUID eventId, RegistrationRole role) {
        get(eventId);
        return repository.listRegistrations(eventId, role);
    }

    private static void validateEvent(UUID organizerId, String name, String city, String state,
                                      String venue, LocalDate startDate, LocalDate endDate) {
        requireId(organizerId, "organizer_id");
        requireText(name, "name", 200);
        requireText(city, "city", 120);
        requireText(state, "state", 80);
        requireOptionalText(venue, "venue", 200);
        if (startDate == null || endDate == null) {
            throw new EventExceptions.ValidationException("start_date and end_date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new EventExceptions.ValidationException("end_date must not be before start_date");
        }
    }

    private static void requireId(UUID value, String field) {
        if (value == null) {
            throw new EventExceptions.ValidationException(field + " is required");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new EventExceptions.ValidationException(field + " is required");
        }
        if (value.trim().length() > maxLength) {
            throw new EventExceptions.ValidationException(field + " must be " + maxLength + " characters or fewer");
        }
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static void requireOptionalText(String value, String field, int maxLength) {
        if (value != null && !value.isBlank() && value.trim().length() > maxLength) {
            throw new EventExceptions.ValidationException(
                    field + " must be " + maxLength + " characters or fewer");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Page(List<Event> events, int nextPageOffset, boolean hasMore) {}
}
