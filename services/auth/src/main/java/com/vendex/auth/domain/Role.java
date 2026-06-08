package com.vendex.auth.domain;

/**
 * Mirrors the proto {@code Role} enum, minus the proto-mandated
 * {@code ROLE_UNSPECIFIED} sentinel. Names match the DB CHECK constraint in
 * V1__users.sql exactly so they round-trip through Spring Data JDBC without
 * needing a converter.
 */
public enum Role {
    VENDOR,
    ATTENDEE,
    ORGANIZER
}
