package com.habittracker.habit_tracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "habit_logs", uniqueConstraints = @UniqueConstraint(
        // Prevents logging the same habit twice on the same day — enforced at DB level
        // Two-layer protection: service layer checks first, DB constraint is the safety
        // net
        columnNames = { "habit_id", "log_date" }, name = "uk_habit_log_date" // Named so it appears clearly in error
                                                                             // messages
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many HabitLogs → One Habit
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    // LocalDate (not LocalDateTime) — we track by DAY, not exact time
    // Reasons: simpler unique constraint, no timezone issues, streak = date
    // arithmetic
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    // True if the user consumed a streak freeze token to cover a missed day
    // Default is false — normal log. True = protected log.
    @Builder.Default
    private boolean usedStreakFreeze = false;

    // When the log entry was created in the system — only set on INSERT, never
    // updated
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
