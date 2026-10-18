package com.example.aidocumentassistant.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByGoogleId(String googleId);

    boolean existsByEmailIgnoreCase(String email);
}
