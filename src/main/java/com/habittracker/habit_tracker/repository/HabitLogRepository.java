package com.habittracker.habit_tracker.repository;

import com.habittracker.habit_tracker.entity.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    // Spring generates: SELECT * FROM habit_logs WHERE habit_id = ? AND log_date = ?
    // Used to prevent duplicate logs — if present, user already logged today
    Optional<HabitLog> findByHabitIdAndLogDate(Long habitId, LocalDate logDate);

    // Fetches all logs for a habit — used for streak calculation
    // Ordered ASC so we can walk through dates chronologically
    List<HabitLog> findByHabitIdOrderByLogDateAsc(Long habitId);

    // Fetches all logs for a habit within a date range — used for weekly/monthly stats
    List<HabitLog> findByHabitIdAndLogDateBetween(Long habitId, LocalDate start, LocalDate end);
}
