package com.habittracker.habit_tracker.controller;

import com.habittracker.habit_tracker.dto.AnalyticsResponseDto;
import com.habittracker.habit_tracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AnalyticsController — REST API Gateway for User Analytics & Progress Dashboard
 *
 * Base Path:
 *   - @RequestMapping("/analytics")
 *   - Combined with server.servlet.context-path=/api/v1 in application.properties
 *   - Full Path: /api/v1/analytics/**
 *
 * Security:
 *   - Protected by Spring Security (requires valid Bearer JWT token).
 *   - Aggregates metrics specifically for the authenticated user (Authentication.getName()).
 */
@RestController                                 // Combines @Controller + @ResponseBody (auto-serializes return values into JSON)
@RequestMapping("/analytics")                   // Sets base URL route prefix for analytics endpoints
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields (analyticsService)
public class AnalyticsController {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    // Injected business logic service for statistics and adherence calculations
    private final AnalyticsService analyticsService;

    // ─────────────────────────────────────────────────────────────
    // 1. GET USER ANALYTICS SUMMARY
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves overall progress statistics and adherence summary for the authenticated user.
     *
     * URL: GET /api/v1/analytics/summary
     * Access: Authenticated Users
     *
     * @param authentication Spring Security object containing logged-in user email
     * @return AnalyticsResponseDto with HTTP 200 OK
     */
    @GetMapping("/summary")                     // Maps HTTP GET /api/v1/analytics/summary to this method
    public ResponseEntity<AnalyticsResponseDto> getUserAnalytics(
            Authentication authentication       // Injected by Spring Security from SecurityContext (contains user email)
    ) {
        // Extract authenticated user's email from JWT principal
        String userEmail = authentication.getName();

        // Fetch aggregated analytics and progress metrics
        AnalyticsResponseDto response = analyticsService.getUserAnalytics(userEmail);

        // Return HTTP 200 OK status with the analytics summary payload
        return ResponseEntity.ok(response);
    }
}
