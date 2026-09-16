package com.habittracker.habit_tracker.dto;

/**
 * AnalyticsResponseDto — Overall Summary Statistics for the User Dashboard
 *
 * Fields:
 *   - totalHabits: Number of habits created by this user
 *   - totalCompletions: Total lifetime check-ins logged across all habits
 *   - highestStreakEver: Longest streak achieved across any of the user's habits
 *   - habitsWithActiveStreaks: How many habits currently have an active streak >= 1 day
 *   - weeklyAdherenceRate: Percentage of habit commitments completed in the past 7 days (0.0 to 100.0%)
 *   - remainingStreakFreezeTokens: Current available streak freeze token balance
 */
public record AnalyticsResponseDto(
        int totalHabits,
        long totalCompletions,
        int highestStreakEver,
        int habitsWithActiveStreaks,
        double weeklyAdherenceRate,
        int remainingStreakFreezeTokens
) {
}
