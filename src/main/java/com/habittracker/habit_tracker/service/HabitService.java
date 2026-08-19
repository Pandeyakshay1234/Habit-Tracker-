package com.habittracker.habit_tracker.service;

import com.habittracker.habit_tracker.dto.HabitRequest;
import com.habittracker.habit_tracker.dto.HabitResponse;
import com.habittracker.habit_tracker.entity.Habit;
import com.habittracker.habit_tracker.entity.HabitLog;
import com.habittracker.habit_tracker.entity.User;
import com.habittracker.habit_tracker.exception.ResourceNotFoundException;
import com.habittracker.habit_tracker.repository.HabitLogRepository;
import com.habittracker.habit_tracker.repository.HabitRepository;
import com.habittracker.habit_tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HabitService — Business Logic for Habit CRUD and Streak Aggregation
 *
 * Responsibilities:
 *   1. Create Habit: Associates the new habit with the authenticated user.
 *   2. Get All Habits: Retrieves all habits owned by the authenticated user with computed streaks.
 *   3. Get Habit By ID: Enforces user isolation (User A cannot view User B's habits).
 *   4. Update Habit: Modifies habit name and description with ownership verification.
 *   5. Delete Habit: Removes habit and cascades deletion of all associated logs.
 *   6. Dynamic Streak Calculation: Computes current and longest streaks from historical logs.
 */
@Service                                        // Marks this class as a Spring Service Bean in the application context
@RequiredArgsConstructor                        // Lombok generates constructor for all final fields for dependency injection
public class HabitService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────

    // Repository for CRUD database operations on habits table
    private final HabitRepository habitRepository;

    // Repository to query habit completion logs for streak calculation
    private final HabitLogRepository habitLogRepository;

    // Repository to look up the authenticated User entity from MySQL
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. CREATE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new habit for the currently authenticated user.
     *
     * @param request   DTO containing habit name and description
     * @param userEmail Email of the authenticated user extracted from JWT
     * @return HabitResponse containing created habit details and initial 0 streak
     */
    @Transactional                              // Wraps database write operations in a transaction
    public HabitResponse createHabit(HabitRequest request, String userEmail) {
        // Fetch authenticated user from database or throw 404 if not found
        User user = getAuthenticatedUser(userEmail);

        // Build new Habit entity and associate it with the authenticated user
        Habit habit = Habit.builder()
                .name(request.name().trim())    // Trim whitespace to ensure clean data
                .description(request.description() != null ? request.description().trim() : null)
                .user(user)                     // Set Foreign Key reference to the user
                .build();

        // Save habit to MySQL database
        Habit savedHabit = habitRepository.save(habit);

        // Newly created habits start with current streak = 0 and longest streak = 0
        return mapToHabitResponse(savedHabit, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET ALL USER HABITS
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves all habits belonging to the authenticated user with calculated streaks.
     *
     * @param userEmail Email of the authenticated user
     * @return List of HabitResponse DTOs
     */
    @Transactional(readOnly = true)             // Optimizes read performance by disabling Hibernate dirty checking
    public List<HabitResponse> getAllUserHabits(String userEmail) {
        // Fetch authenticated user from database
        User user = getAuthenticatedUser(userEmail);

        // Fetch all habits belonging to this specific user ID
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        // Transform each Habit entity to HabitResponse DTO including calculated streak metrics
        return habits.stream()
                .map(this::enrichHabitWithStreaks)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET HABIT BY ID
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves a single habit by ID, enforcing that it belongs to the authenticated user.
     *
     * @param habitId   ID of the habit to fetch
     * @param userEmail Email of the authenticated user
     * @return HabitResponse DTO with calculated streaks
     */
    @Transactional(readOnly = true)             // Read-only transaction optimization
    public HabitResponse getHabitById(Long habitId, String userEmail) {
        // Fetch authenticated user
        User user = getAuthenticatedUser(userEmail);

        // Find habit ensuring it belongs to this user (IDOR prevention)
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Return mapped DTO with calculated streaks
        return enrichHabitWithStreaks(habit);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. UPDATE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Updates an existing habit's name and description.
     *
     * @param habitId   ID of the habit to update
     * @param request   DTO containing updated fields
     * @param userEmail Email of the authenticated user
     * @return HabitResponse DTO with updated details
     */
    @Transactional                              // Transactional write
    public HabitResponse updateHabit(Long habitId, HabitRequest request, String userEmail) {
        // Fetch authenticated user
        User user = getAuthenticatedUser(userEmail);

        // Find existing habit belonging to this user
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Update entity state
        habit.setName(request.name().trim());
        habit.setDescription(request.description() != null ? request.description().trim() : null);

        // Save updated habit to database
        Habit updatedHabit = habitRepository.save(habit);

        // Return enriched response with streak metrics preserved
        return enrichHabitWithStreaks(updatedHabit);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. DELETE HABIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Deletes a habit and cascades deletion of all associated logs.
     *
     * @param habitId   ID of the habit to delete
     * @param userEmail Email of the authenticated user
     */
    @Transactional                              // Transactional write
    public void deleteHabit(Long habitId, String userEmail) {
        // Fetch authenticated user
        User user = getAuthenticatedUser(userEmail);

        // Find habit belonging to this user or throw 404
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Delete from database (CascadeType.ALL removes child logs)
        habitRepository.delete(habit);
    }

    // ─────────────────────────────────────────────────────────────
    // 6. HELPER METHODS & STREAK CALCULATION ALGORITHM
    // ─────────────────────────────────────────────────────────────

    /**
     * Helper to look up the authenticated User entity by email.
     *
     * @param email User email from authentication context
     * @return User entity
     */
    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /**
     * Helper to compute streak statistics and map Habit entity to HabitResponse DTO.
     *
     * @param habit Habit entity
     * @return Enriched HabitResponse DTO
     */
    private HabitResponse enrichHabitWithStreaks(Habit habit) {
        // Query all logs for this habit sorted chronologically
        List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateAsc(habit.getId());

        // Calculate current streak and longest historical streak
        StreakStats stats = calculateStreaks(logs);

        // Map and return response
        return mapToHabitResponse(habit, stats.currentStreak(), stats.longestStreak());
    }

    /**
     * Streak Calculation Algorithm:
     * Computes current streak and longest streak from chronological habit logs.
     *
     * Rules:
     *   - Current streak is unbroken count of consecutive days up to today (or yesterday if today is not yet logged).
     *   - Longest streak is the maximum consecutive sequence of logged dates across the habit's entire history.
     *
     * @param logs Chronologically sorted list of HabitLog entries
     * @return StreakStats record containing (currentStreak, longestStreak)
     */
    public StreakStats calculateStreaks(List<HabitLog> logs) {
        // If no logs exist, streaks are 0
        if (logs == null || logs.isEmpty()) {
            return new StreakStats(0, 0);
        }

        // Collect all distinct logged dates into a Set for O(1) membership lookup
        Set<LocalDate> loggedDates = logs.stream()
                .map(HabitLog::getLogDate)
                .collect(Collectors.toSet());

        // ── 1. CALCULATE CURRENT STREAK ──────────────────────────────
        LocalDate today = LocalDate.now();
        int currentStreak = 0;

        // Check if today was completed; if not, check if yesterday was completed to keep streak alive
        LocalDate checkDate = loggedDates.contains(today) ? today : today.minusDays(1);

        // Count consecutive days backward from checkDate
        while (loggedDates.contains(checkDate)) {
            currentStreak++;
            checkDate = checkDate.minusDays(1);
        }

        // ── 2. CALCULATE LONGEST STREAK ──────────────────────────────
        // Extract sorted unique dates list
        List<LocalDate> sortedDates = loggedDates.stream()
                .sorted()
                .toList();

        int longestStreak = 0;
        int runningStreak = 0;
        LocalDate previousDate = null;

        for (LocalDate date : sortedDates) {
            if (previousDate == null || date.equals(previousDate.plusDays(1))) {
                // First entry OR consecutive day: increment running streak
                runningStreak++;
            } else {
                // Gap in dates: reset running streak to 1 for the new chain
                runningStreak = 1;
            }
            // Update longest streak achieved
            longestStreak = Math.max(longestStreak, runningStreak);
            previousDate = date;
        }

        return new StreakStats(currentStreak, longestStreak);
    }

    /**
     * Maps Habit entity + computed streak integers to HabitResponse record.
     */
    private HabitResponse mapToHabitResponse(Habit habit, int currentStreak, int longestStreak) {
        return new HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getDescription(),
                currentStreak,
                longestStreak,
                habit.getCreatedAt()
        );
    }

    /**
     * Immutable internal record holding computed streak metrics.
     */
    public record StreakStats(int currentStreak, int longestStreak) {}
}
