package com.habittracker.habit_tracker.service;

import com.habittracker.habit_tracker.dto.HabitLogRequestDto;
import com.habittracker.habit_tracker.dto.HabitLogResponseDto;
import com.habittracker.habit_tracker.entity.Habit;
import com.habittracker.habit_tracker.entity.HabitLog;
import com.habittracker.habit_tracker.entity.User;
import com.habittracker.habit_tracker.exception.DuplicateResourceException;
import com.habittracker.habit_tracker.exception.ResourceNotFoundException;
import com.habittracker.habit_tracker.repository.HabitLogRepository;
import com.habittracker.habit_tracker.repository.HabitRepository;
import com.habittracker.habit_tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * HabitLogService — Business Logic for Daily Habit Check-ins & Streak Freeze Redemption
 *
 * Core Responsibilities:
 *   1. Normal / Backdated Check-in: Logs habit completion for a specific date (defaults to today).
 *   2. Duplicate Prevention: Ensures a habit can only be logged once per day.
 *   3. Future Date Guard: Rejects logs with dates in the future.
 *   4. Streak Freeze Redemption: Consumes 1 freeze token to bridge a missed day without breaking streak.
 *   5. History Retrieval & Undo: View all logs for a habit or undo an accidental log entry.
 */
@Service                                        // Registers this class as a Spring-managed Service Bean
@RequiredArgsConstructor                        // Lombok generates constructor for all final repositories
public class HabitLogService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES (Injected via constructor)
    // ─────────────────────────────────────────────────────────────
    private final HabitLogRepository habitLogRepository;
    private final HabitRepository habitRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. LOG HABIT COMPLETION (CHECK-IN)
    // ─────────────────────────────────────────────────────────────
    /**
     * Marks a habit as completed for a given date (defaults to today if date is null).
     *
     * @param habitId the ID of the habit to log
     * @param request optional request containing custom log date (for backdated logging)
     * @param userEmail the authenticated user's email from JWT
     * @return HabitLogResponseDto containing saved log details
     */
    @Transactional                              // Executes within a database transaction
    public HabitLogResponseDto logHabitCompletion(Long habitId, HabitLogRequestDto request, String userEmail) {
        // Step 1: Find the logged-in user by email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find habit and verify ownership (IDOR protection)
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Determine target log date (defaults to today if not provided in request)
        LocalDate targetDate = (request != null && request.logDate() != null) ? request.logDate() : LocalDate.now();

        // Step 4: Validate that the date is not in the future
        if (targetDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot log habit completion for a future date: " + targetDate);
        }

        // Step 5: Check if this habit is already logged for the target date
        if (habitLogRepository.findByHabitIdAndLogDate(habit.getId(), targetDate).isPresent()) {
            throw new DuplicateResourceException("Habit is already logged for date: " + targetDate);
        }

        // Step 6: Build new HabitLog entity (normal check-in -> usedStreakFreeze is false)
        HabitLog habitLog = HabitLog.builder()
                .habit(habit)                   // Links log to parent habit entity
                .logDate(targetDate)             // Sets the completion date
                .usedStreakFreeze(false)         // Normal completion (not a streak freeze)
                .build();

        // Step 7: Save to MySQL database
        HabitLog savedLog = habitLogRepository.save(habitLog);

        // Step 8: Map saved entity to HabitLogResponseDto and return
        return mapToResponseDto(savedLog);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. REDEEM STREAK FREEZE TOKEN
    // ─────────────────────────────────────────────────────────────
    /**
     * Consumes 1 streak freeze token to log a missed day, preserving the streak chain.
     *
     * @param habitId the ID of the habit to protect
     * @param request optional request containing the missed date to freeze (defaults to today)
     * @param userEmail the authenticated user's email from JWT
     * @return HabitLogResponseDto with usedStreakFreeze = true
     */
    @Transactional
    public HabitLogResponseDto useStreakFreeze(Long habitId, HabitLogRequestDto request, String userEmail) {
        // Step 1: Find the logged-in user by email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Check if user has available streak freeze tokens
        if (user.getStreakFreezeTokens() <= 0) {
            throw new IllegalStateException("No streak freeze tokens available. Current balance: 0");
        }

        // Step 3: Find habit and verify ownership
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 4: Determine target date to freeze
        LocalDate targetDate = (request != null && request.logDate() != null) ? request.logDate() : LocalDate.now();

        // Step 5: Validate that the freeze date is not in the future
        if (targetDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot use streak freeze for a future date: " + targetDate);
        }

        // Step 6: Check if date is already logged
        if (habitLogRepository.findByHabitIdAndLogDate(habit.getId(), targetDate).isPresent()) {
            throw new DuplicateResourceException("Habit is already logged for date: " + targetDate);
        }

        // Step 7: Deduct 1 streak freeze token from user balance
        user.setStreakFreezeTokens(user.getStreakFreezeTokens() - 1);
        userRepository.save(user);              // Updates user's remaining freeze tokens in DB

        // Step 8: Build HabitLog entity with usedStreakFreeze set to true
        HabitLog freezeLog = HabitLog.builder()
                .habit(habit)
                .logDate(targetDate)
                .usedStreakFreeze(true)          // Flags this log as a redeemed freeze token
                .build();

        // Step 9: Save freeze log entry to DB
        HabitLog savedLog = habitLogRepository.save(freezeLog);

        // Step 10: Map saved entity to DTO and return
        return mapToResponseDto(savedLog);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GET ALL LOGS FOR A HABIT
    // ─────────────────────────────────────────────────────────────
    /**
     * Retrieves all log entries for a habit in chronological order (oldest to newest).
     *
     * @param habitId the ID of the habit
     * @param userEmail the authenticated user's email
     * @return List of HabitLogResponseDto
     */
    @Transactional(readOnly = true)             // Read-only transaction for high performance
    public List<HabitLogResponseDto> getHabitLogs(Long habitId, String userEmail) {
        // Step 1: Find user by email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find habit ensuring ownership
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Fetch all logs ordered by date ascending
        List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateAsc(habit.getId());

        // Step 4: Map each entity to DTO
        List<HabitLogResponseDto> responseList = new ArrayList<>();
        for (HabitLog log : logs) {
            responseList.add(mapToResponseDto(log));
        }

        // Step 5: Return response list
        return responseList;
    }

    // ─────────────────────────────────────────────────────────────
    // 4. DELETE / UNDO HABIT LOG
    // ─────────────────────────────────────────────────────────────
    /**
     * Deletes a log entry for a specific date (e.g. if user logged by mistake).
     * If the deleted log used a freeze token, the token is automatically refunded to the user.
     *
     * @param habitId the ID of the habit
     * @param logDate the date of the log to delete
     * @param userEmail the authenticated user's email
     */
    @Transactional
    public void deleteHabitLog(Long habitId, LocalDate logDate, String userEmail) {
        // Step 1: Find user by email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Step 2: Find habit ensuring ownership
        Habit habit = habitRepository.findByIdAndUserId(habitId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        // Step 3: Find existing log for the specified date
        HabitLog habitLog = habitLogRepository.findByHabitIdAndLogDate(habit.getId(), logDate)
                .orElseThrow(() -> new ResourceNotFoundException("No log found for habit id: " + habitId + " on date: " + logDate));

        // Step 4: If this log used a streak freeze, refund the token back to the user
        if (habitLog.isUsedStreakFreeze()) {
            user.setStreakFreezeTokens(user.getStreakFreezeTokens() + 1);
            userRepository.save(user);          // Saves refunded token balance
        }

        // Step 5: Delete log from database
        habitLogRepository.delete(habitLog);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. HELPER: CONVERT ENTITY TO RESPONSE DTO
    // ─────────────────────────────────────────────────────────────
    private HabitLogResponseDto mapToResponseDto(HabitLog habitLog) {
        return new HabitLogResponseDto(
                habitLog.getId(),
                habitLog.getHabit().getId(),
                habitLog.getLogDate(),
                habitLog.isUsedStreakFreeze(),
                habitLog.getCreatedAt()
        );
    }
}
