package com.ordevia.aidev.api;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Problem> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Problem(Instant.now(), 404, ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, SecurityException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Problem> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Problem(Instant.now(), 409, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Problem> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new Problem(Instant.now(), 400, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new Problem(Instant.now(), 400, "Invalid request"));
    }

    record Problem(Instant timestamp, int status, String message) {}
}
