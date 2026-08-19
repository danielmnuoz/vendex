package com.vendex.event.repository;

import com.vendex.event.domain.Event;
import com.vendex.event.domain.EventRegistration;
import com.vendex.event.domain.RegistrationRole;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EventRepository {

    private static final RowMapper<Event> EVENT_MAPPER = (rs, rowNum) -> new Event(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("organizer_id"),
            rs.getString("name"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getString("venue"),
            rs.getDate("start_date").toLocalDate(),
            rs.getDate("end_date").toLocalDate(),
            rs.getString("description"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static final RowMapper<EventRegistration> REGISTRATION_MAPPER = (rs, rowNum) ->
            new EventRegistration(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("event_id"),
                    (UUID) rs.getObject("user_id"),
                    RegistrationRole.valueOf(rs.getString("role").toUpperCase()),
                    rs.getString("booth"),
                    rs.getTimestamp("registered_at").toInstant()
            );

    private final NamedParameterJdbcTemplate jdbc;

    public EventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Event insertEvent(UUID organizerId, String name, String city, String state,
                             String venue, LocalDate startDate, LocalDate endDate,
                             String description, Instant now) {
        return jdbc.queryForObject(
                """
                INSERT INTO events
                    (organizer_id, name, city, state, venue, start_date, end_date,
                     description, created_at, updated_at)
                VALUES
                    (:organizer_id, :name, :city, :state, :venue, :start_date, :end_date,
                     :description, :now, :now)
                RETURNING *
                """,
                eventParams(organizerId, name, city, state, venue, startDate, endDate, description, now),
                EVENT_MAPPER
        );
    }

    public Optional<Event> updateEvent(UUID eventId, String name, String city, String state,
                                       String venue, LocalDate startDate, LocalDate endDate,
                                       String description, Instant now) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    UPDATE events SET
                        name = :name,
                        city = :city,
                        state = :state,
                        venue = :venue,
                        start_date = :start_date,
                        end_date = :end_date,
                        description = :description,
                        updated_at = :now
                    WHERE id = :event_id
                    RETURNING *
                    """,
                    new MapSqlParameterSource()
                            .addValue("event_id", eventId)
                            .addValue("name", name)
                            .addValue("city", city)
                            .addValue("state", state)
                            .addValue("venue", venue)
                            .addValue("start_date", Date.valueOf(startDate))
                            .addValue("end_date", Date.valueOf(endDate))
                            .addValue("description", description)
                            .addValue("now", Timestamp.from(now)),
                    EVENT_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Event> findEvent(UUID eventId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM events WHERE id = :id",
                    new MapSqlParameterSource("id", eventId),
                    EVENT_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Event> listEvents(int limit, int offset) {
        return jdbc.query(
                """
                SELECT * FROM events
                ORDER BY start_date ASC, name ASC, id ASC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource()
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                EVENT_MAPPER
        );
    }

    public EventRegistration insertRegistration(UUID eventId, UUID userId,
                                                  RegistrationRole role, String booth,
                                                  Instant now) {
        return jdbc.queryForObject(
                """
                INSERT INTO event_registrations
                    (event_id, user_id, role, booth, registered_at)
                VALUES (:event_id, :user_id, :role, :booth, :registered_at)
                RETURNING *
                """,
                new MapSqlParameterSource()
                        .addValue("event_id", eventId)
                        .addValue("user_id", userId)
                        .addValue("role", role.name().toLowerCase())
                        .addValue("booth", booth)
                        .addValue("registered_at", Timestamp.from(now)),
                REGISTRATION_MAPPER
        );
    }

    public Optional<EventRegistration> deleteRegistration(UUID eventId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    DELETE FROM event_registrations
                    WHERE event_id = :event_id AND user_id = :user_id
                    RETURNING *
                    """,
                    new MapSqlParameterSource()
                            .addValue("event_id", eventId)
                            .addValue("user_id", userId),
                    REGISTRATION_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<EventRegistration> listRegistrations(UUID eventId, RegistrationRole role) {
        return jdbc.query(
                """
                SELECT * FROM event_registrations
                WHERE event_id = :event_id AND role = :role
                ORDER BY registered_at ASC, id ASC
                """,
                new MapSqlParameterSource()
                        .addValue("event_id", eventId)
                        .addValue("role", role.name().toLowerCase()),
                REGISTRATION_MAPPER
        );
    }

    public List<EventRegistration> listRegistrationsForUser(
            UUID userId, RegistrationRole role, int limit, int offset) {
        return jdbc.query(
                """
                SELECT * FROM event_registrations
                WHERE user_id = :user_id AND role = :role
                ORDER BY registered_at DESC, id ASC
                LIMIT :limit OFFSET :offset
                """,
                new MapSqlParameterSource()
                        .addValue("user_id", userId)
                        .addValue("role", role.name().toLowerCase())
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                REGISTRATION_MAPPER
        );
    }

    private static MapSqlParameterSource eventParams(
            UUID organizerId, String name, String city, String state, String venue,
            LocalDate startDate, LocalDate endDate, String description, Instant now) {
        return new MapSqlParameterSource()
                .addValue("organizer_id", organizerId)
                .addValue("name", name)
                .addValue("city", city)
                .addValue("state", state)
                .addValue("venue", venue)
                .addValue("start_date", Date.valueOf(startDate))
                .addValue("end_date", Date.valueOf(endDate))
                .addValue("description", description)
                .addValue("now", Timestamp.from(now));
    }
}
