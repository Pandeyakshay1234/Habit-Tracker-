package com.habittracker.habit_tracker.scheduler;

import com.habittracker.habit_tracker.entity.Habit;
import com.habittracker.habit_tracker.entity.User;
import com.habittracker.habit_tracker.repository.HabitRepository;
import com.habittracker.habit_tracker.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ReminderScheduler — Automated Background Task for Daily 9:00 PM Habit
 * Reminders
 *
 * How it works:
 * 1. Triggers every night at 21:00:00 (9:00 PM) server time via @Scheduled
 * cron.
 * 2. Queries all habits that have not yet been logged for today.
 * 3. Groups pending habits by User so each user receives exactly 1 consolidated
 * email.
 * 4. Dispatches HTML reminder emails via EmailService.
 */
@Component // Registers this scheduler as a Spring-managed Bean
@RequiredArgsConstructor // Lombok generates constructor for habitRepository & emailService
@Slf4j // Lombok provides logger instance for job telemetry
public class ReminderScheduler {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCIES
    // ─────────────────────────────────────────────────────────────
    private final HabitRepository habitRepository;
    private final EmailService emailService;

    // ─────────────────────────────────────────────────────────────
    // 1. SCHEDULED CRON JOB (9:00 PM EVERY NIGHT)
    // ─────────────────────────────────────────────────────────────
    /**
     * Automated cron task:
     * - Cron Expression: "0 0 21 * * *"
     * - Fields: [sec min hour day-of-month month day-of-week]
     * - Executes: Every single day at 21:00:00 (9:00 PM)
     */
    // Live Test: Runs at 10:22:00 PM (22:22) Indian Standard Time (IST)
    @Scheduled(cron = "0 22 22 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true) // Ensures session remains open while reading user entities
    public void sendDailyHabitReminders() {
        LocalDate today = LocalDate.now();
        log.info("Starting automated 9:00 PM Daily Habit Reminder Job for date: {}", today);

        // Step 1: Query all habits that have not been logged on today's date
        List<Habit> unloggedHabits = habitRepository.findHabitsNotLoggedOnDate(today);

        // If all habits are already completed across the entire system, log and return
        if (unloggedHabits.isEmpty()) {
            log.info("All users have completed all their habits today! No reminders needed.");
            return;
        }

        // Step 2: Group unlogged habits by their owner (User) using Java Streams
        // This ensures a user with 5 pending habits receives ONE consolidated email
        // instead of 5 separate emails
        Map<User, List<Habit>> userHabitsMap = unloggedHabits.stream()
                .collect(Collectors.groupingBy(Habit::getUser));

        log.info("Found {} users with a total of {} uncompleted habits for today",
                userHabitsMap.size(), unloggedHabits.size());

        // Step 3: Iterate through each user and dispatch their custom HTML reminder
        // email
        for (Map.Entry<User, List<Habit>> entry : userHabitsMap.entrySet()) {
            User user = entry.getKey();
            List<Habit> pendingHabits = entry.getValue();

            // Send styled HTML reminder
            emailService.sendDailyReminderEmail(user, pendingHabits);
        }

        log.info("Completed automated 9:00 PM Daily Habit Reminder Job successfully.");
    }
}
