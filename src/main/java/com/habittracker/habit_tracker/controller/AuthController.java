package com.habittracker.habit_tracker.controller;

import com.habittracker.habit_tracker.dto.AuthResponse;
import com.habittracker.habit_tracker.dto.LoginRequest;
import com.habittracker.habit_tracker.dto.RegisterRequest;
import com.habittracker.habit_tracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — REST API Gateway for User Authentication
 *
 * Exposes endpoints for:
 *   1. POST /api/v1/auth/register  → Register a new user (HTTP 201 Created)
 *   2. POST /api/v1/auth/login     → Authenticate existing user (HTTP 200 OK)
 *
 * Base Path:
 *   - @RequestMapping("/auth")
 *   - Combined with server.servlet.context-path=/api/v1 in application.properties
 *   - Full path: /api/v1/auth/**
 */
@RestController                                 // Combines @Controller + @ResponseBody (auto-serializes return objects into JSON)
@RequestMapping("/auth")                        // Sets base path prefix for all endpoints in this controller
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields (authService)
public class AuthController {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    // Injected business logic service for authentication
    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────
    // 1. REGISTER ENDPOINT
    // ─────────────────────────────────────────────────────────────

    /**
     * Handles user registration HTTP POST requests.
     *
     * URL: POST /api/v1/auth/register
     * Access: Public (configured in SecurityConfig permitAll)
     *
     * @param request JSON request body containing name, email, and password
     * @return ResponseEntity with AuthResponse (JWT + user profile) and HTTP 201 Created
     */
    @PostMapping("/register")                   // Maps HTTP POST requests on /auth/register to this method
    public ResponseEntity<AuthResponse> register(
            @Valid                              // Triggers Bean Validation (@NotBlank, @Email, @Size) on request fields
            @RequestBody                        // Deserializes incoming JSON payload into RegisterRequest record
            RegisterRequest request
    ) {
        // Delegate user creation and token generation to AuthService
        AuthResponse response = authService.register(request);

        // Return HTTP 201 Created status with AuthResponse JSON body
        // 201 Created is the standard REST status when a new resource (user) is created
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. LOGIN ENDPOINT
    // ─────────────────────────────────────────────────────────────

    /**
     * Handles user login HTTP POST requests.
     *
     * URL: POST /api/v1/auth/login
     * Access: Public (configured in SecurityConfig permitAll)
     *
     * @param request JSON request body containing email and password
     * @return ResponseEntity with AuthResponse (fresh JWT + user profile) and HTTP 200 OK
     */
    @PostMapping("/login")                      // Maps HTTP POST requests on /auth/login to this method
    public ResponseEntity<AuthResponse> login(
            @Valid                              // Triggers Bean Validation on email and password fields
            @RequestBody                        // Deserializes incoming JSON payload into LoginRequest record
            LoginRequest request
    ) {
        // Delegate credential authentication and token generation to AuthService
        AuthResponse response = authService.login(request);

        // Return HTTP 200 OK status with AuthResponse JSON body
        // 200 OK is the standard REST status for successful authentication query
        return ResponseEntity.ok(response);
    }
}
