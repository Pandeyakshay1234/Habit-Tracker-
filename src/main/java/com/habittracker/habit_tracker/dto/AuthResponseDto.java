package com.habittracker.habit_tracker.dto;

public record AuthResponseDto(
        String token,
        Long userId,
        String name,
        String email,
        int streakFreezeTokens
) {
}
