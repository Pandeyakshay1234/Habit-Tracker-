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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HabitService — Business Logic for Habit Operations (CRUD & Streak Calculation)
 *
 * Easy to explain in interviews:
 * 1. Each user can only see and modify their OWN habits (security check using findByIdAndUserId).
 * 2. Habits start with 0 streak when created.
 * 3. Streaks are calculated dynamically from the habit_logs table.
 */
@Service                                        // Tells Spring to manage this class as a Service Bean
@RequiredArgsConstructor                        // Lombok generates constructor for all final repositories
public class HabitService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES (Injected via constructor by Spring)
    // ─────────────────────────────────────────────────────────────
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. CREATE HABIT
    // ─────────────────────────────────────────────────────────────
    @Transactional                              // Saves data safely in a database transaction
    public HabitResponse createHabit(HabitRequest request, String userEmail) {
        // Step 1: Find the logged-in user from DB using their email (from JWT token)
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Create a new Habit entity and link it to the user
        Habit habit = Habit.builder()
                .name(request.name().trim())
                .description(request.description())
                .user(user)                     // Sets the foreign key (user_id)
                .build();

        // Step 3: Save to MySQL database
        Habit savedHabit = habitRepository.save(habit);

        // Step 4: Return response (new habit starts with 0 current and 0 longest streak)
        return mapToResponse(savedHabit, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. GET ALL HABITS OF LOGGED-IN USER
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)             // Read-only transaction (faster, no dirty checking)
    public List<HabitResponse> getAllUserHabits(String userEmail) {
        // Step 1: Find the logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Fetch all habits belonging to this user ID
        List<Habit> habits = habitRepository.findByUserId(user.getId());

        // Step 3: Loop through each habit, calculate streaks, and build the response list
        List<HabitResponse> responseList = new ArrayList<>();
        for (Habit habit : habits) {
            int currentStreak = calculateCurrentStreak(habit.getId());
            int longestStreak = calculateLongestStreak(habit.getId());
            responseList.add(mapToResponse(habit, currentStreak, longestStreak));
        }

        // Step 4: Return the list of habit responses
        return responseList;
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET SINGLE HABIT BY ID
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public HabitResponse getHabitById(Long habitId, String userEmail) {
        // Step 1: Find the logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find habit by ID AND ensure it belongs to this user (prevents IDOR attacks)
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Calculate streaks
        int currentStreak = calculateCurrentStreak(habit.getId());
        int longestStreak = calculateLongestStreak(habit.getId());

        // Step 4: Return response
        return mapToResponse(habit, currentStreak, longestStreak);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. UPDATE HABIT
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public HabitResponse updateHabit(Long habitId, HabitRequest request, String userEmail) {
        // Step 1: Find the logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find existing habit belonging to this user
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Update the fields
        habit.setName(request.name().trim());
        habit.setDescription(request.description());

        // Step 4: Save updated habit to DB
        Habit updatedHabit = habitRepository.save(habit);

        // Step 5: Return updated response with calculated streaks
        int currentStreak = calculateCurrentStreak(updatedHabit.getId());
        int longestStreak = calculateLongestStreak(updatedHabit.getId());
        return mapToResponse(updatedHabit, currentStreak, longestStreak);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. DELETE HABIT
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public void deleteHabit(Long habitId, String userEmail) {
        // Step 1: Find the logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find habit belonging to this user
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Delete habit (CascadeType.ALL in entity deletes all associated logs automatically)
        habitRepository.delete(habit);
    }

    // ─────────────────────────────────────────────────────────────
    // 6. HELPER: CALCULATE CURRENT STREAK
    // ─────────────────────────────────────────────────────────────
    /**
     * How Current Streak works:
     * - We check consecutive days backward from today.
     * - If today is not logged yet, we start from yesterday (streak is still alive today).
     * - If yesterday was also missed, streak is 0.
     */
    public int calculateCurrentStreak(Long habitId) {
        // Fetch all logs of this habit
        List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateAsc(habitId);
        if (logs.isEmpty()) {
            return 0;
        }

        // Put all logged dates into a HashSet for fast O(1) lookup
        Set<LocalDate> loggedDates = new HashSet<>();
        for (HabitLog log : logs) {
            loggedDates.add(log.getLogDate());
        }

        LocalDate today = LocalDate.now();
        int streak = 0;

        // If today is logged, start counting backward from today.
        // If today is NOT logged yet, start from yesterday.
        LocalDate checkDate = loggedDates.contains(today) ? today : today.minusDays(1);

        // Count consecutive days going backwards
        while (loggedDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1); // Move to previous day
        }

        return streak;
    }

    // ─────────────────────────────────────────────────────────────
    // 7. HELPER: CALCULATE LONGEST STREAK
    // ─────────────────────────────────────────────────────────────
    /**
     * How Longest Streak works:
     * - Walk through all logs sorted from oldest to newest.
     * - If next date is exactly (previousDate + 1 day), increase current count.
     * - If there is a gap, reset current count to 1.
     * - Track the maximum count achieved.
     */
    public int calculateLongestStreak(Long habitId) {
        // Fetch all logs sorted by date ascending (oldest to newest)
        List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateAsc(habitId);
        if (logs.isEmpty()) {
            return 0;
        }

        int maxStreak = 0;
        int currentCount = 0;
        LocalDate previousDate = null;

        for (HabitLog log : logs) {
            LocalDate currentDate = log.getLogDate();

            if (previousDate == null) {
                // First log entry
                currentCount = 1;
            } else if (currentDate.equals(previousDate.plusDays(1))) {
                // Consecutive day (e.g. Aug 19 -> Aug 20) -> increase streak
                currentCount++;
            } else if (currentDate.equals(previousDate)) {
                // Same day log (safety check) -> ignore
                continue;
            } else {
                // Gap between dates -> reset count to 1 for the new streak
                currentCount = 1;
            }

            // Keep track of the highest streak seen
            if (currentCount > maxStreak) {
                maxStreak = currentCount;
            }

            previousDate = currentDate;
        }

        return maxStreak;
    }

    // ─────────────────────────────────────────────────────────────
    // 8. HELPER: CONVERT ENTITY TO RESPONSE DTO
    // ─────────────────────────────────────────────────────────────
    private HabitResponse mapToResponse(Habit habit, int currentStreak, int longestStreak) {
        return new HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getDescription(),
                currentStreak,
                longestStreak,
                habit.getCreatedAt()
        );
    }
}
