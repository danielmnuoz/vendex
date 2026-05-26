package com.vendex.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
public record User(
        @Id UUID id,
        String email,
        String passwordHash,
        Role role,
        String shopName,
        String city,
        String state,
        Instant createdAt,
        Instant updatedAt
) {}
