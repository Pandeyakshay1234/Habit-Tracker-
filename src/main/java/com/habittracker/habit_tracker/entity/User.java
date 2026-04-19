package com.habittracker.habit_tracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity                         // Marks this class as a JPA entity → maps to a DB table
@Table(name = "users")          // Explicit table name. "user" is a reserved keyword in MySQL — would break queries
@Getter                         // Lombok: generates getters for all fields
@Setter                         // Lombok: generates setters for all fields
@NoArgsConstructor              // Lombok: generates empty constructor — REQUIRED by JPA spec
@AllArgsConstructor             // Lombok: generates constructor with all fields — needed internally by @Builder
@Builder                        // Lombok: enables User.builder().email("x").name("y").build() pattern
public class User {

    @Id                                                      // Primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)      // MySQL AUTO_INCREMENT — DB assigns the id
    private Long id;

    @Column(nullable = false, unique = true)  // NOT NULL + UNIQUE constraint in DB. No two users can share email.
    private String email;

    @Column(nullable = false)  // NOT NULL — password is mandatory
    private String password;   // Stored as BCrypt hash, NEVER plain text

    @Column(nullable = false)
    private String name;

    // Streak freeze feature: 1 token per week protects streak if user misses a day
    // @Builder.Default: tells Lombok to use this initializer value when building via builder()
    // Without @Builder.Default, builder would set this to 0 even though we wrote = 1
    @Builder.Default
    private int streakFreezeTokens = 1;

    // One User → Many Habits
    // mappedBy = "user" → the "user" field in Habit class owns this relationship (holds the FK)
    // cascade = ALL → save/delete User automatically saves/deletes their Habits
    // orphanRemoval = true → if a Habit is removed from this list in Java, it's deleted from DB
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default            // Same reason: without this, builder sets list to null
    private List<Habit> habits = new ArrayList<>();

    @CreationTimestamp          // Hibernate auto-sets this on INSERT. You never touch it.
    @Column(updatable = false)  // Never included in UPDATE statements
    private LocalDateTime createdAt;

    @UpdateTimestamp            // Hibernate auto-updates this on every UPDATE
    private LocalDateTime updatedAt;
}
