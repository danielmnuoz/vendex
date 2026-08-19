package com.vendex.event.grpc;

import com.vendex.event.domain.EventRegistration;
import com.vendex.event.domain.RegistrationRole;
import com.vendex.event.service.EventService;
import com.vendex.event.v1.CreateEventRequest;
import com.vendex.event.v1.CreateEventResponse;
import com.vendex.event.v1.EventServiceGrpc;
import com.vendex.event.v1.GetEventAttendeesRequest;
import com.vendex.event.v1.GetEventRegistrationsResponse;
import com.vendex.event.v1.GetEventRequest;
import com.vendex.event.v1.GetEventResponse;
import com.vendex.event.v1.GetEventVendorsRequest;
import com.vendex.event.v1.ListEventsRequest;
import com.vendex.event.v1.ListEventsResponse;
import com.vendex.event.v1.ListEventRegistrationsForUserRequest;
import com.vendex.event.v1.ListEventRegistrationsResponse;
import com.vendex.event.v1.RegisterForEventRequest;
import com.vendex.event.v1.RegisterForEventResponse;
import com.vendex.event.v1.UnregisterFromEventRequest;
import com.vendex.event.v1.UnregisterFromEventResponse;
import com.vendex.event.v1.UpdateEventRequest;
import com.vendex.event.v1.UpdateEventResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;
import java.util.UUID;

@GrpcService
public class EventGrpcService extends EventServiceGrpc.EventServiceImplBase {

    private final EventService service;

    public EventGrpcService(EventService service) {
        this.service = service;
    }

    @Override
    public void createEvent(CreateEventRequest request, StreamObserver<CreateEventResponse> obs) {
        try {
            var event = service.create(
                    uuid(request.getOrganizerId(), "organizer_id"),
                    request.getName(), request.getCity(), request.getState(), request.getVenue(),
                    date(request.getStartDate(), "start_date"),
                    date(request.getEndDate(), "end_date"),
                    request.getDescription());
            obs.onNext(CreateEventResponse.newBuilder().setEvent(toProto(event)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void updateEvent(UpdateEventRequest request, StreamObserver<UpdateEventResponse> obs) {
        try {
            var event = service.update(
                    uuid(request.getEventId(), "event_id"),
                    uuid(request.getOrganizerId(), "organizer_id"),
                    request.getName(), request.getCity(), request.getState(), request.getVenue(),
                    date(request.getStartDate(), "start_date"),
                    date(request.getEndDate(), "end_date"),
                    request.getDescription());
            obs.onNext(UpdateEventResponse.newBuilder().setEvent(toProto(event)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void registerForEvent(RegisterForEventRequest request,
                                 StreamObserver<RegisterForEventResponse> obs) {
        try {
            EventRegistration registration = service.register(
                    uuid(request.getEventId(), "event_id"),
                    uuid(request.getUserId(), "user_id"),
                    fromProto(request.getRole()),
                    request.getBooth());
            obs.onNext(RegisterForEventResponse.newBuilder()
                    .setRegistration(toProto(registration)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void unregisterFromEvent(UnregisterFromEventRequest request,
                                    StreamObserver<UnregisterFromEventResponse> obs) {
        try {
            service.unregister(
                    uuid(request.getEventId(), "event_id"),
                    uuid(request.getUserId(), "user_id"));
            obs.onNext(UnregisterFromEventResponse.newBuilder().build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void listEvents(ListEventsRequest request, StreamObserver<ListEventsResponse> obs) {
        try {
            var page = service.list(request.getPageSize(), request.getPageOffset());
            var response = ListEventsResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.events().forEach(event -> response.addEvents(toProto(event)));
            obs.onNext(response.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getEvent(GetEventRequest request, StreamObserver<GetEventResponse> obs) {
        try {
            var event = service.get(uuid(request.getEventId(), "event_id"));
            obs.onNext(GetEventResponse.newBuilder().setEvent(toProto(event)).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getEventVendors(GetEventVendorsRequest request,
                                StreamObserver<GetEventRegistrationsResponse> obs) {
        listRegistrations(request.getEventId(), RegistrationRole.VENDOR, obs);
    }

    @Override
    public void getEventAttendees(GetEventAttendeesRequest request,
                                  StreamObserver<GetEventRegistrationsResponse> obs) {
        listRegistrations(request.getEventId(), RegistrationRole.ATTENDEE, obs);
    }

    @Override
    public void listEventRegistrationsForUser(
            ListEventRegistrationsForUserRequest request,
            StreamObserver<ListEventRegistrationsResponse> obs) {
        try {
            var page = service.listRegistrationsForUser(
                    uuid(request.getUserId(), "user_id"),
                    fromProto(request.getRole()),
                    request.getPageSize(),
                    request.getPageOffset());
            var response = ListEventRegistrationsResponse.newBuilder()
                    .setHasMore(page.hasMore())
                    .setNextPageOffset(page.nextPageOffset());
            page.registrations().forEach(registration ->
                    response.addRegistrations(toProto(registration)));
            obs.onNext(response.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    private void listRegistrations(String eventId, RegistrationRole role,
                                   StreamObserver<GetEventRegistrationsResponse> obs) {
        try {
            var response = GetEventRegistrationsResponse.newBuilder();
            service.listRegistrations(uuid(eventId, "event_id"), role)
                    .forEach(registration -> response.addRegistrations(toProto(registration)));
            obs.onNext(response.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    private static com.vendex.event.v1.Event toProto(com.vendex.event.domain.Event event) {
        return com.vendex.event.v1.Event.newBuilder()
                .setId(event.id().toString())
                .setOrganizerId(event.organizerId().toString())
                .setName(event.name())
                .setCity(event.city())
                .setState(event.state())
                .setVenue(nullToEmpty(event.venue()))
                .setStartDate(event.startDate().toString())
                .setEndDate(event.endDate().toString())
                .setDescription(nullToEmpty(event.description()))
                .setCreatedAtEpochSeconds(event.createdAt().getEpochSecond())
                .setUpdatedAtEpochSeconds(event.updatedAt().getEpochSecond())
                .build();
    }

    private static com.vendex.event.v1.EventRegistration toProto(EventRegistration registration) {
        return com.vendex.event.v1.EventRegistration.newBuilder()
                .setId(registration.id().toString())
                .setEventId(registration.eventId().toString())
                .setUserId(registration.userId().toString())
                .setRole(registration.role() == RegistrationRole.VENDOR
                        ? com.vendex.event.v1.RegistrationRole.REGISTRATION_ROLE_VENDOR
                        : com.vendex.event.v1.RegistrationRole.REGISTRATION_ROLE_ATTENDEE)
                .setBooth(nullToEmpty(registration.booth()))
                .setRegisteredAtEpochSeconds(registration.registeredAt().getEpochSecond())
                .build();
    }

    private static RegistrationRole fromProto(com.vendex.event.v1.RegistrationRole role) {
        return switch (role) {
            case REGISTRATION_ROLE_VENDOR -> RegistrationRole.VENDOR;
            case REGISTRATION_ROLE_ATTENDEE -> RegistrationRole.ATTENDEE;
            default -> throw new IllegalArgumentException("registration role is required");
        };
    }

    private static UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be ISO-8601 YYYY-MM-DD");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
