package com.habittracker.habit_tracker.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A standard shape for all error responses in our API.
 * Whenever an API fails, the client will ALWAYS receive JSON matching this structure.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors // Will be populated only for validation failures (@NotBlank etc.)
) {
}
