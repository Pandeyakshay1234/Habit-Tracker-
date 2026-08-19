package com.habittracker.habit_tracker.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HabitLogResponseDto(
        Long id,
        Long habitId,
        LocalDate logDate,
        boolean usedStreakFreeze,
        LocalDateTime createdAt
) {
}
