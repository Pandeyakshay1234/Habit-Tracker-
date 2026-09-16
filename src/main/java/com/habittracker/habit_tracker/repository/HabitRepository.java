package com.habittracker.habit_tracker.repository;

import com.habittracker.habit_tracker.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    // Custom JPQL Query: Finds all habits that have NOT been logged on a given date
    // Uses JOIN FETCH h.user to eagerly load the owner and avoid LazyInitializationException / N+1 queries
    @Query("""
        SELECT h FROM Habit h 
        JOIN FETCH h.user 
        WHERE h.id NOT IN (
            SELECT hl.habit.id FROM HabitLog hl WHERE hl.logDate = :targetDate
        )
    """)
    List<Habit> findHabitsNotLoggedOnDate(@Param("targetDate") LocalDate targetDate);
}
