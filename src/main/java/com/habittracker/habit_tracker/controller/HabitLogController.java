package com.habittracker.habit_tracker.controller;

import com.habittracker.habit_tracker.dto.HabitLogRequestDto;
import com.habittracker.habit_tracker.dto.HabitLogResponseDto;
import com.habittracker.habit_tracker.service.HabitLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * HabitLogController — REST API Gateway for Daily Habit Check-ins & Streak Freeze Operations
 *
 * Base Path:
 *   - @RequestMapping("/habits/{habitId}/logs")
 *   - Combined with server.servlet.context-path=/api/v1 in application.properties
 *   - Full Path: /api/v1/habits/{habitId}/logs/**
 *
 * Security:
 *   - All endpoints are PROTECTED by Spring Security (requires Bearer JWT).
 *   - User identity is extracted directly from the Spring SecurityContext (Authentication.getName()).
 *   - Every action verifies habit ownership in the service layer to prevent IDOR vulnerabilities.
 */
@RestController                                 // Combines @Controller + @ResponseBody (serializes return values into JSON)
@RequestMapping("/habits/{habitId}/logs")       // Nested REST route under a specific habit ID
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields (habitLogService)
public class HabitLogController {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    // Injected business logic service for habit logging and streak freezes
    private final HabitLogService habitLogService;

    // ─────────────────────────────────────────────────────────────
    // 1. LOG HABIT COMPLETION (CHECK-IN)
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks a habit completed for today or an optional past date.
     *
     * URL: POST /api/v1/habits/{habitId}/logs
     * Access: Authenticated Users
     *
     * @param habitId the ID of the habit to log
     * @param request optional JSON body containing custom log date (defaults to today if empty/null)
     * @param authentication Spring Security object containing logged-in user email
     * @return HabitLogResponseDto with HTTP 201 Created
     */
    @PostMapping                                // Maps HTTP POST /api/v1/habits/{habitId}/logs to this method
    public ResponseEntity<HabitLogResponseDto> logHabit(
            @PathVariable                       // Extracts {habitId} from URL path
            Long habitId,
            @RequestBody(required = false)      // Optional body: client can send empty body for today's check-in
            HabitLogRequestDto request,
            Authentication authentication       // Injected from SecurityContext (contains authenticated user email)
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delegate daily check-in logic to service layer
        HabitLogResponseDto response = habitLogService.logHabitCompletion(habitId, request, userEmail);

        // Return HTTP 201 Created status with saved log data
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. REDEEM STREAK FREEZE TOKEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Consumes 1 streak freeze token to log a missed day and protect the user's streak.
     *
     * URL: POST /api/v1/habits/{habitId}/logs/freeze
     * Access: Authenticated Users
     *
     * @param habitId the ID of the habit to protect
     * @param request optional JSON body containing the missed date to freeze (defaults to today)
     * @param authentication Spring Security object containing logged-in user email
     * @return HabitLogResponseDto with HTTP 201 Created
     */
    @PostMapping("/freeze")                     // Maps HTTP POST /api/v1/habits/{habitId}/logs/freeze
    public ResponseEntity<HabitLogResponseDto> useStreakFreeze(
            @PathVariable                       // Extracts {habitId} from URL path
            Long habitId,
            @RequestBody(required = false)      // Optional body for specifying which missed date to freeze
            HabitLogRequestDto request,
            Authentication authentication       // Injected from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delegate streak freeze redemption to service layer
        HabitLogResponseDto response = habitLogService.useStreakFreeze(habitId, request, userEmail);

        // Return HTTP 201 Created status with saved freeze log data
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET LOG HISTORY FOR A HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves all log entries for a habit in chronological order (calendar/history view).
     *
     * URL: GET /api/v1/habits/{habitId}/logs
     * Access: Authenticated Users
     *
     * @param habitId the ID of the habit
     * @param authentication Spring Security object containing logged-in user email
     * @return List of HabitLogResponseDto with HTTP 200 OK
     */
    @GetMapping                                 // Maps HTTP GET /api/v1/habits/{habitId}/logs to this method
    public ResponseEntity<List<HabitLogResponseDto>> getHabitLogs(
            @PathVariable                       // Extracts {habitId} from URL path
            Long habitId,
            Authentication authentication       // Injected from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Fetch chronological log entries for this habit
        List<HabitLogResponseDto> logs = habitLogService.getHabitLogs(habitId, userEmail);

        // Return HTTP 200 OK status with the log history list
        return ResponseEntity.ok(logs);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. DELETE / UNDO A HABIT LOG
    // ─────────────────────────────────────────────────────────────

    /**
     * Deletes a log entry for a specific date (and auto-refunds token if it was a freeze).
     *
     * URL: DELETE /api/v1/habits/{habitId}/logs/{date}
     * Example: DELETE /api/v1/habits/1/logs/2026-09-12
     * Access: Authenticated Users
     *
     * @param habitId the ID of the habit
     * @param date the LocalDate to delete (formatted as YYYY-MM-DD)
     * @param authentication Spring Security object containing logged-in user email
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{date}")                   // Maps HTTP DELETE /api/v1/habits/{habitId}/logs/{date}
    public ResponseEntity<Void> deleteHabitLog(
            @PathVariable                       // Extracts {habitId} from URL path
            Long habitId,
            @PathVariable                       // Extracts {date} from URL path
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // Enforces ISO-8601 YYYY-MM-DD date format parsing
            LocalDate date,
            Authentication authentication       // Injected from SecurityContext
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Delete the log entry (and refund token if it was a freeze)
        habitLogService.deleteHabitLog(habitId, date, userEmail);

        // Return HTTP 204 No Content
        return ResponseEntity.noContent().build();
    }
}
