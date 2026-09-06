package com.habittracker.habit_tracker.controller;

import com.habittracker.habit_tracker.dto.HabitRequestDto;
import com.habittracker.habit_tracker.dto.HabitResponseDto;
import com.habittracker.habit_tracker.service.HabitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HabitController — REST API Gateway for Habit Management (CRUD Operations)
 *
 * Base Path:
 *   - @RequestMapping("/habits")
 *   - Combined with server.servlet.context-path=/api/v1 in application.properties
 *   - Full Path: /api/v1/habits/**
 *
 * Security:
 *   - All endpoints are PROTECTED by Spring Security (requires Bearer JWT).
 *   - The user identity (email) is extracted directly from the Spring SecurityContext (Authentication.getName()).
 *   - This prevents IDOR attacks because clients cannot spoof or tamper with user identity.
 */
@RestController                                 // Marks this class as a REST Controller (auto-serializes responses to JSON)
@RequestMapping("/habits")                      // Sets base URL route prefix for all habit endpoints
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields (habitService)
public class HabitController {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    // Injected business logic service for habit operations
    private final HabitService habitService;

    // ─────────────────────────────────────────────────────────────
    // 1. CREATE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new habit for the authenticated user.
     *
     * URL: POST /api/v1/habits
     * Access: Authenticated Users
     *
     * @param request JSON body with habit name and description
     * @param authentication Spring Security object containing logged-in user email
     * @return HabitResponseDto with HTTP 201 Created
     */
    @PostMapping                                // Maps HTTP POST /api/v1/habits to this method
    public ResponseEntity<HabitResponseDto> createHabit(
            @Valid                              // Triggers validation on HabitRequestDto fields (@NotBlank, @Size)
            @RequestBody                        // Deserializes JSON payload from request body into HabitRequestDto
            HabitRequestDto request,
            Authentication authentication       // Injected by Spring Security from SecurityContext (contains user email)
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delegate habit creation to service layer
        HabitResponseDto response = habitService.createHabit(request, userEmail);

        // Return HTTP 201 Created status with newly created habit payload
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET ALL HABITS OF LOGGED-IN USER
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves all habits belonging to the authenticated user.
     *
     * URL: GET /api/v1/habits
     * Access: Authenticated Users
     *
     * @param authentication Spring Security object containing logged-in user email
     * @return List of HabitResponseDto with HTTP 200 OK
     */
    @GetMapping                                 // Maps HTTP GET /api/v1/habits to this method
    public ResponseEntity<List<HabitResponseDto>> getAllHabits(
            Authentication authentication       // Injected by Spring Security from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Fetch all habits with dynamically calculated streaks for this user
        List<HabitResponseDto> habits = habitService.getAllUserHabits(userEmail);

        // Return HTTP 200 OK status with the habit list
        return ResponseEntity.ok(habits);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET HABIT BY ID
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves a single habit by its ID (ensuring ownership).
     *
     * URL: GET /api/v1/habits/{id}
     * Access: Authenticated Users
     *
     * @param id the ID of the habit to retrieve
     * @param authentication Spring Security object containing logged-in user email
     * @return HabitResponseDto with HTTP 200 OK
     */
    @GetMapping("/{id}")                        // Maps HTTP GET /api/v1/habits/{id} to this method
    public ResponseEntity<HabitResponseDto> getHabitById(
            @PathVariable                       // Extracts {id} route variable from URL path
            Long id,
            Authentication authentication       // Injected by Spring Security from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Fetch habit details ensuring it belongs to the authenticated user
        HabitResponseDto habit = habitService.getHabitById(id, userEmail);

        // Return HTTP 200 OK status with habit data
        return ResponseEntity.ok(habit);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. UPDATE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Updates an existing habit's name and description.
     *
     * URL: PUT /api/v1/habits/{id}
     * Access: Authenticated Users
     *
     * @param id the ID of the habit to update
     * @param request JSON body with updated name and description
     * @param authentication Spring Security object containing logged-in user email
     * @return Updated HabitResponseDto with HTTP 200 OK
     */
    @PutMapping("/{id}")                        // Maps HTTP PUT /api/v1/habits/{id} to this method
    public ResponseEntity<HabitResponseDto> updateHabit(
            @PathVariable                       // Extracts {id} route variable from URL path
            Long id,
            @Valid                              // Validates updated fields (@NotBlank, @Size)
            @RequestBody                        // Deserializes JSON payload into HabitRequestDto
            HabitRequestDto request,
            Authentication authentication       // Injected by Spring Security from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delegate habit update to service layer
        HabitResponseDto updatedHabit = habitService.updateHabit(id, request, userEmail);

        // Return HTTP 200 OK status with updated habit data
        return ResponseEntity.ok(updatedHabit);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. DELETE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Deletes a habit and cascades to delete all its associated logs.
     *
     * URL: DELETE /api/v1/habits/{id}
     * Access: Authenticated Users
     *
     * @param id the ID of the habit to delete
     * @param authentication Spring Security object containing logged-in user email
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")                     // Maps HTTP DELETE /api/v1/habits/{id} to this method
    public ResponseEntity<Void> deleteHabit(
            @PathVariable                       // Extracts {id} route variable from URL path
            Long id,
            Authentication authentication       // Injected by Spring Security from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delete the habit ensuring ownership
        habitService.deleteHabit(id, userEmail);

        // Return HTTP 204 No Content (standard REST response when resource is deleted without return body)
        return ResponseEntity.noContent().build();
    }
}
