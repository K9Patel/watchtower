
package com.watchtower.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * AJT — @ControllerAdvice: Global exception handler.
 * Intercepts all unhandled exceptions from @RestController methods
 * and returns structured JSON error responses instead of ugly stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — entity not found (e.g. device ID doesn't exist)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return buildError(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    // 400 — bad path variable type (e.g. /api/devices/abc when Long expected)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        String msg = String.format("Parameter '%s' must be of type %s",
                ex.getName(), requiredType != null ? requiredType.getSimpleName() : "unknown");
        return buildError(HttpStatus.BAD_REQUEST, "Invalid parameter", msg);
    }

    // 400 — invalid enum value (e.g. device type or severity)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Invalid argument", ex.getMessage());
    }

    // 500 — catch-all for unexpected server errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     error);
        body.put("message",   message != null ? message : "No details available");
        return ResponseEntity.status(status).body(body);
    }
}
