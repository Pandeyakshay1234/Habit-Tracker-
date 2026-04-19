package com.habittracker.habit_tracker.repository;

import com.habittracker.habit_tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring generates: SELECT * FROM users WHERE email = ?
    // Optional → forces caller to handle "user not found" case explicitly
    Optional<User> findByEmail(String email);

    // Spring generates: SELECT COUNT(*) > 0 FROM users WHERE email = ?
    // Used during registration to reject duplicate emails
    boolean existsByEmail(String email);
}
