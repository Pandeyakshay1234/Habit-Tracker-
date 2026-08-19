package com.habittracker.habit_tracker.service;

import com.habittracker.habit_tracker.dto.AuthResponse;
import com.habittracker.habit_tracker.dto.LoginRequest;
import com.habittracker.habit_tracker.dto.RegisterRequest;
import com.habittracker.habit_tracker.entity.User;
import com.habittracker.habit_tracker.exception.DuplicateResourceException;
import com.habittracker.habit_tracker.exception.ResourceNotFoundException;
import com.habittracker.habit_tracker.repository.UserRepository;
import com.habittracker.habit_tracker.security.CustomUserDetailsService;
import com.habittracker.habit_tracker.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — Business Logic for User Authentication
 *
 * Responsibilities:
 *   1. Register: Validates duplicate email, hashes password with BCrypt, saves User to DB, returns JWT + user details
 *   2. Login: Authenticates credentials via AuthenticationManager, fetches User, returns JWT + user details
 */
@Service                                        // Registers this class as a Spring Service Bean in the application context
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields (enables constructor dependency injection)
public class AuthService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────

    // Repository for database operations on User entity (MySQL)
    private final UserRepository userRepository;

    // BCrypt password encoder for secure one-way password hashing
    private final PasswordEncoder passwordEncoder;

    // JWT utility for generating and signing JSON Web Tokens
    private final JwtUtil jwtUtil;

    // Spring Security's AuthenticationManager to verify user credentials during login
    private final AuthenticationManager authenticationManager;

    // Service to load Spring Security's UserDetails for token generation
    private final CustomUserDetailsService userDetailsService;

    // ─────────────────────────────────────────────────────────────
    // 1. REGISTER NEW USER
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user account.
     *
     * Steps:
     *   1. Check if email already exists in DB → throw 409 Conflict if duplicate
     *   2. Hash plain-text password using BCrypt
     *   3. Build User entity and save to MySQL
     *   4. Generate JWT token for the new user
     *   5. Return AuthResponse containing token and user profile
     *
     * @param request the registration details (name, email, password)
     * @return AuthResponse with JWT and user information
     */
    @Transactional                              // Ensures the registration database operation is executed within a transaction
    public AuthResponse register(RegisterRequest request) {

        // Normalize email to lowercase and trim whitespace to prevent duplicates with different casing
        String normalizedEmail = request.email().toLowerCase().trim();

        // Check if an account already exists with this email address
        if (userRepository.existsByEmail(normalizedEmail)) {
            // Throw custom Conflict exception (mapped to HTTP 409 by GlobalExceptionHandler)
            throw new DuplicateResourceException("Email is already registered: " + normalizedEmail);
        }

        // Build a new User entity using Lombok builder pattern
        User user = User.builder()
                .name(request.name().trim())                                // Set trimmed user name
                .email(normalizedEmail)                                     // Set normalized email
                .password(passwordEncoder.encode(request.password()))       // Hash password with BCrypt (never store plain text)
                .build();                                                   // streakFreezeTokens defaults to 1 via @Builder.Default

        // Save the user entity to MySQL database and obtain persisted entity with generated ID
        User savedUser = userRepository.save(user);

        // Load UserDetails representation of the saved user for token generation
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());

        // Generate a new signed JWT token for this user
        String token = jwtUtil.generateToken(userDetails);

        // Return AuthResponse DTO containing JWT and user profile info
        return new AuthResponse(
                token,                                                      // Generated JWT token
                savedUser.getId(),                                          // Database auto-generated primary key
                savedUser.getName(),                                        // User's display name
                savedUser.getEmail(),                                       // User's email
                savedUser.getStreakFreezeTokens()                           // Initial streak freeze token balance (1)
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 2. LOGIN USER
    // ─────────────────────────────────────────────────────────────

    /**
     * Authenticates an existing user and issues a JWT token.
     *
     * Steps:
     *   1. Delegate credential verification to Spring Security's AuthenticationManager
     *   2. Fetch User entity from MySQL DB
     *   3. Generate a fresh JWT token
     *   4. Return AuthResponse containing token and user profile
     *
     * @param request the login credentials (email, password)
     * @return AuthResponse with JWT and user information
     */
    @Transactional(readOnly = true)             // Optimizes transaction for read-only database query
    public AuthResponse login(LoginRequest request) {

        // Normalize email to match registration casing
        String normalizedEmail = request.email().toLowerCase().trim();

        // Delegate authentication to AuthenticationManager
        // If password does not match or user doesn't exist, AuthenticationManager throws BadCredentialsException
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,                                    // Principal: user email
                        request.password()                                  // Credentials: raw password (AuthenticationProvider verifies against hash)
                )
        );

        // If authenticate() succeeds without exception, fetch the full User entity from MySQL
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        // Load UserDetails representation needed by JwtUtil
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // Generate a fresh JWT token for this login session
        String token = jwtUtil.generateToken(userDetails);

        // Return AuthResponse DTO containing JWT and user profile info
        return new AuthResponse(
                token,                                                      // Freshly generated JWT token
                user.getId(),                                               // User's ID
                user.getName(),                                             // User's display name
                user.getEmail(),                                            // User's email
                user.getStreakFreezeTokens()                                // Current streak freeze balance
        );
    }
}
