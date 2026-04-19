package com.habittracker.habit_tracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (User, Habit, HabitLog) does not exist in the database.
 * The @ResponseStatus annotation tells Spring to return a 404 Not Found if this exception is not explicitly caught.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
