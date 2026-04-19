package com.habittracker.habit_tracker.dto;

import java.time.LocalDateTime;

public record HabitResponse(
        Long id,
        String name,
        String description,
        int currentStreak,
        int longestStreak,
        LocalDateTime createdAt
) {
}
