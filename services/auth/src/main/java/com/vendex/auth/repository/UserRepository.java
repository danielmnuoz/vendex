package com.vendex.auth.repository;

import com.vendex.auth.domain.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudRepository<User, UUID> {

    // @Param binds :email explicitly so the query doesn't rely solely on the
    // -parameters compiler flag (set in the parent POM) being retained.
    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
