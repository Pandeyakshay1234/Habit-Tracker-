package com.habittracker.habit_tracker.dto;

public record AuthResponse(
        String token,
        Long userId,
        String name,
        String email,
        int streakFreezeTokens
) {
}
