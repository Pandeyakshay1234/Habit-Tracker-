package com.habittracker.habit_tracker.service;

import com.habittracker.habit_tracker.dto.AnalyticsResponseDto;
import com.habittracker.habit_tracker.entity.Habit;
import com.habittracker.habit_tracker.entity.User;
import com.habittracker.habit_tracker.exception.ResourceNotFoundException;
import com.habittracker.habit_tracker.repository.HabitLogRepository;
import com.habittracker.habit_tracker.repository.HabitRepository;
import com.habittracker.habit_tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * AnalyticsService — Business Logic for User Habit Statistics & Progress Insights
 *
 * Calculates:
 *   1. Total Habits & Total Lifetime Check-ins
 *   2. Highest Streak Ever achieved across all habits
 *   3. Count of habits with currently active streaks (> 0)
 *   4. 7-Day Adherence Rate (% of planned habits completed over the last 7 days)
 *   5. Available Streak Freeze balance
 */
@Service                                        // Registers this class as a Spring-managed Service Bean
@RequiredArgsConstructor                        // Lombok generates constructor for all final dependencies
public class AnalyticsService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final UserRepository userRepository;
    private final HabitService habitService;

    // ─────────────────────────────────────────────────────────────
    // 1. GET USER ANALYTICS SUMMARY
    // ─────────────────────────────────────────────────────────────

    /**
     * Aggregates and calculates overall summary statistics for the authenticated user.
     *
     * @param userEmail the email of the logged-in user (from JWT)
     * @return AnalyticsResponseDto with aggregated metrics
     */
    @Transactional(readOnly = true)             // Read-only transaction for optimal database read performance
    public AnalyticsResponseDto getUserAnalytics(String userEmail) {
        // Step 1: Find the user in DB by email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Fetch all habits belonging to this user
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        // If the user has no habits yet, return empty zero metrics immediately
        if (habits.isEmpty()) {
            return new AnalyticsResponseDto(
                    0,                          // totalHabits
                    0L,                         // totalCompletions
                    0,                          // highestStreakEver
                    0,                          // habitsWithActiveStreaks
                    0.0,                        // weeklyAdherenceRate
                    user.getStreakFreezeTokens()// remaining freeze tokens
            );
        }

        // Step 3: Calculate total active habits count
        int totalHabits = habits.size();

        // Step 4: Count total lifetime check-in logs for this user across all habits
        long totalCompletions = habitLogRepository.countByHabitUserId(user.getId());

        // Step 5: Loop through habits to determine streaks metrics
        int highestStreakEver = 0;
        int habitsWithActiveStreaks = 0;

        for (Habit habit : habits) {
            // Calculate current streak for this habit
            int currentStreak = habitService.calculateCurrentStreak(habit.getId());

            // Calculate historical longest streak for this habit
            int longestStreak = habitService.calculateLongestStreak(habit.getId());

            // If current streak is alive (> 0), increment active counter
            if (currentStreak > 0) {
                habitsWithActiveStreaks++;
            }

            // Track the overall maximum streak across all user habits
            if (longestStreak > highestStreakEver) {
                highestStreakEver = longestStreak;
            }
        }

        // Step 6: Calculate 7-day adherence rate
        // We look at the window from (today - 6 days) to today (7 days total)
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        // Count how many check-in logs were recorded for this user in the last 7 days
        long pastWeekLogsCount = habitLogRepository.countByHabitUserIdAndLogDateBetween(user.getId(), sevenDaysAgo, today);

        // Maximum possible check-ins in a 7-day window = (number of habits * 7 days)
        long maxPossibleLogs = (long) totalHabits * 7;

        // Calculate percentage: (actual logs / maximum possible) * 100
        double weeklyAdherenceRate = 0.0;
        if (maxPossibleLogs > 0) {
            double rate = ((double) pastWeekLogsCount / maxPossibleLogs) * 100.0;
            // Round to 2 decimal places (e.g. 85.71%)
            weeklyAdherenceRate = Math.round(rate * 100.0) / 100.0;
        }

        // Step 7: Build and return the completed AnalyticsResponseDto
        return new AnalyticsResponseDto(
                totalHabits,
                totalCompletions,
                highestStreakEver,
                habitsWithActiveStreaks,
                weeklyAdherenceRate,
                user.getStreakFreezeTokens()
        );
    }
}
