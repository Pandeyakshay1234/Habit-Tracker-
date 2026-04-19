package com.habittracker.habit_tracker.dto;

import jakarta.validation.constraints.NotBlank;

public record HabitRequest(
                @NotBlank(message = "Habit name is required") String name,

                String description) {

}
