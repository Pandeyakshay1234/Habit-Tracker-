package com.habittracker.habit_tracker.repository;

import com.habittracker.habit_tracker.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HabitRepository extends JpaRepository<Habit, Long> {

    // Spring generates: SELECT * FROM habits WHERE user_id = ?
    // Returns all habits belonging to a specific user
    List<Habit> findByUserId(Long userId);

    // Used to verify a habit belongs to the requesting user before any update/delete
    // Prevents user A from accessing user B's habits
    Optional<Habit> findByIdAndUserId(Long id, Long userId);
}
