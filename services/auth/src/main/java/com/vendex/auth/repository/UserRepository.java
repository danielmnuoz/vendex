package com.vendex.auth.repository;

import com.vendex.auth.domain.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudRepository<User, UUID> {

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(String email);
}
