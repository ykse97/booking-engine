package com.booking.engine.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API.
 * Catches and formats all exceptions into standardized JSON responses.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------------------- Constants ----------------------

    private static final String ERROR_NOT_FOUND = "Not Found";
    private static final String ERROR_VALIDATION = "Validation Error";
    private static final String ERROR_BAD_REQUEST = "Bad Request";
    private static final String ERROR_UNAUTHORIZED = "Unauthorized";
    private static final String ERROR_FORBIDDEN = "Forbidden";
    private static final String ERROR_INTERNAL = "Internal Server Error";

    // ---------------------- 404 Not Found ----------------------

    /**
     * Handles entity not found exceptions.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                ERROR_NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ---------------------- 400 Bad Request ----------------------

    /**
     * Handles Spring validation errors (@Valid).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation failed for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        this::toFriendlyFieldMessage,
                        (existing, replacement) -> existing));

        String message = fieldErrors.size() == 1
                ? fieldErrors.values().iterator().next()
                : "Please review the highlighted fields and try again.";

        ValidationErrorResponse response = new ValidationErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_VALIDATION,
                message,
                fieldErrors,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles malformed JSON payloads and invalid scalar formats.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed request body for {}: {}",
                request.getRequestURI(), ex.getMessage());

        String message = "Some submitted values could not be read. Please review the form and try again.";
        Throwable current = ex;
        InvalidFormatException invalidFormat = null;

        while (current != null) {
            if (current instanceof InvalidFormatException formatException) {
                invalidFormat = formatException;
                break;
            }
            current = current.getCause();
        }

        if (invalidFormat != null) {
            String fieldName = invalidFormat.getPath().isEmpty()
                    ? null
                    : invalidFormat.getPath().get(invalidFormat.getPath().size() - 1).getFieldName();
            String fieldLabel = toFieldLabel(fieldName);
            Class<?> targetType = invalidFormat.getTargetType();

            if (LocalDate.class.equals(targetType)) {
                message = fieldName != null
                        ? "Please choose a real calendar date for " + fieldLabel + "."
                        : "Please choose a real calendar date and try again.";
            } else if (LocalTime.class.equals(targetType)) {
                message = fieldName != null
                        ? "Please enter a valid time for " + fieldLabel + "."
                        : "Please enter a valid time and try again.";
            } else {
                message = fieldName != null
                        ? fieldLabel + " contains an invalid value."
                        : "One or more submitted values are invalid. Please review the form and try again.";
            }
        }

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                message,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles constraint violation errors (e.g., @NotNull on path variables).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("Constraint violation for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        String message = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_VALIDATION,
                message,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles booking validation exceptions (business logic).
     */
    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<ErrorResponse> handleBookingValidation(
            BookingValidationException ex,
            HttpServletRequest request) {

        log.warn("Booking validation failed for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles payment processing exceptions.
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessing(
            PaymentProcessingException ex,
            HttpServletRequest request) {

        log.warn("Payment processing failed for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles illegal state exceptions.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("Illegal state for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument for request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles database integrity violations (FK/UNIQUE/NOT NULL).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        String causeMessage = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        log.warn("Data integrity violation for request to {}: {}",
                request.getRequestURI(), causeMessage);

        String friendly = "Request conflicts with existing data constraints";
        String lower = causeMessage != null ? causeMessage.toLowerCase() : "";
        if (lower.contains("display_order") || lower.contains("barber_display_order")) {
            friendly = "Display order is already in use by another barber. Please choose a different position.";
        }

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                friendly,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles authentication failures (invalid credentials/token).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.warn("Authentication failed for request to {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                ERROR_UNAUTHORIZED,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handles authorization failures (insufficient role).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied for request to {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                ERROR_FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // ---------------------- 500 Internal Server Error ----------------------

    /**
     * Handles all unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error for request to {}: {}",
                request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ERROR_INTERNAL,
                "An unexpected error occurred",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /*
     * Rewrites framework-style validation messages into plain-language feedback
     * so admins see what needs to be fixed without reading technical jargon.
     *
     * @param error Spring field validation error
     * @return admin-friendly validation message
     */
    private String toFriendlyFieldMessage(FieldError error) {
        String fieldLabel = toFieldLabel(error.getField());
        String rawMessage = error.getDefaultMessage();

        if (rawMessage == null || rawMessage.isBlank()) {
            return fieldLabel + " is invalid.";
        }

        String normalizedMessage = rawMessage.trim();
        String lowerCaseMessage = normalizedMessage.toLowerCase(Locale.ROOT);

        if (lowerCaseMessage.equals("must not be null")
                || lowerCaseMessage.equals("must not be blank")
                || lowerCaseMessage.equals("must not be empty")
                || lowerCaseMessage.equals("не должно равняться null")
                || lowerCaseMessage.equals("не должно быть пустым")) {
            return fieldLabel + " is required.";
        }

        if (lowerCaseMessage.startsWith("must be greater than or equal to ")) {
            String minValue = normalizedMessage.substring("must be greater than or equal to ".length());
            return fieldLabel + " must be " + minValue + " or greater.";
        }

        if (lowerCaseMessage.startsWith("must be less than or equal to ")) {
            String maxValue = normalizedMessage.substring("must be less than or equal to ".length());
            return fieldLabel + " must be " + maxValue + " or less.";
        }

        if (lowerCaseMessage.startsWith("size must be between ")) {
            return fieldLabel + " length is outside the allowed range.";
        }

        return normalizedMessage;
    }

    /*
     * Converts raw field names like "applyToAllBarbers" into readable labels
     * that work better in UI-facing validation and parsing messages.
     *
     * @param fieldName raw binding field name
     * @return human-readable field label
     */
    private String toFieldLabel(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "This field";
        }

        String simplified = fieldName.replaceAll(".*\\.", "").replaceAll("\\[[^\\]]*\\]", "");
        simplified = simplified.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);

        if (simplified.isEmpty()) {
            return "This field";
        }

        return Character.toUpperCase(simplified.charAt(0)) + simplified.substring(1);
    }
}
