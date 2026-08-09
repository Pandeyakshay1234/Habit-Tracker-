package com.habittracker.habit_tracker.security;

import com.habittracker.habit_tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * CustomUserDetailsService — DB ↔ Spring Security Bridge
 *
 * Problem: Spring Security needs to load a user during login,
 *          but has no idea about our MySQL DB or JPA setup.
 *
 * Solution: Implement UserDetailsService — Spring's contract.
 *           We fetch the user from DB and return a UserDetails object
 *           that Spring Security can understand.
 *
 * Flow:
 *   POST /auth/login { email, password }
 *     → AuthenticationManager asks for user
 *     → calls loadUserByUsername(email)
 *     → we go to UserRepository → MySQL
 *     → wrap DB entity into Spring's UserDetails
 *     → Spring compares BCrypt-hashed passwords
 *     → ✅ match → authenticated  ❌ mismatch → 401
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    /**
     * We need UserRepository to fetch the user from MySQL by email.
     * Injected via constructor thanks to @RequiredArgsConstructor (Lombok).
     */
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // CORE METHOD — UserDetailsService contract
    // ─────────────────────────────────────────────────────────────

    /**
     * Spring Security calls this method when it needs to authenticate a user.
     *
     * @param username — despite the name, we treat this as an EMAIL
     *                   (Spring's interface uses "username" generically)
     * @return UserDetails — Spring Security's representation of an authenticated user
     * @throws UsernameNotFoundException — MUST throw this exact exception if not found
     *                                     (Spring Security expects it — not a generic RuntimeException)
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. Go to MySQL and find the user by email
        //    orElseThrow → if not found, throw UsernameNotFoundException (Spring expects this)
        com.habittracker.habit_tracker.entity.User user = userRepository
                .findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + username
                ));

        // 2. Return Spring Security's built-in User object
        //    Built from OUR entity's fields:
        //      - getEmail()    → the "username" Spring uses for identity
        //      - getPassword() → the BCrypt-hashed password (Spring will compare this)
        //      - Collections.emptyList() → no roles/authorities yet (will add in Phase 2)
        //
        // NOTE: We do NOT make our entity implement UserDetails directly.
        //       That would couple our DB layer to Spring Security — bad design.
        //       This service is the translator between the two worlds.
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.emptyList()  // authorities/roles — empty for now
        );
    }
}
