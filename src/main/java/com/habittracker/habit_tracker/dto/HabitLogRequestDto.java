package com.habittracker.habit_tracker.dto;

import java.time.LocalDate;

/**
 * HabitLogRequestDto — Request payload for logging a habit completion or redeeming a freeze.
 *
 * Fields:
 *   - logDate: Optional specific date (e.g., for backdated logging).
 *              If omitted/null in request JSON, the backend automatically defaults to LocalDate.now().
 */
public record HabitLogRequestDto(
        LocalDate logDate                       // Optional: Specific date to log (defaults to today if null)
) {
}
