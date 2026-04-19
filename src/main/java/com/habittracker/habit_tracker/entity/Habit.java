package com.habittracker.habit_tracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "habits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) // Habit name is mandatory
    private String name;

    private String description; // Optional — no @Column constraint needed, nullable by default

    // Many Habits → One User
    // fetch = LAZY → do NOT load User data from DB unless explicitly accessed
    // Why LAZY? Loading 100 habits should not fire 100 extra queries to load each
    // user (N+1 problem)
    // @JoinColumn → tells Hibernate the FK column name in the habits table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // user_id column in habits table, cannot be null
    private User user;

    // One Habit → Many HabitLogs
    // mappedBy = "habit" → the "habit" field in HabitLog owns the relationship
    // CascadeType.ALL → deleting a Habit also deletes all its logs
    // orphanRemoval = true → removing a log from this list deletes it from DB
    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HabitLog> logs = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
