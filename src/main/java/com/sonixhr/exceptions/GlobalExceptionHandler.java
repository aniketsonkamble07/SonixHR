package com.sonixhr.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =====================================================
    // HANDLE BAD CREDENTIALS (Wrong password)
    // =====================================================
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(
            BadCredentialsException ex) {

        log.warn("Authentication failed: Invalid credentials");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "success", false,
                        "message", "Invalid email or password",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE USERNAME NOT FOUND
    // =====================================================
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameNotFoundException(
            UsernameNotFoundException ex) {

        log.warn("User not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "success", false,
                        "message", "Invalid email or password",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE VALIDATION ERRORS
    // =====================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = getFriendlyMessage(fieldName, error.getDefaultMessage());
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Please check the highlighted fields",
                        "errors", errors,
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE FIELD-LEVEL VALIDATION ERRORS PROGRAMMATICALLY
    // =====================================================
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            ValidationException ex) {

        log.warn("Validation failed programmatically: {}", ex.getErrors());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Please check the highlighted fields",
                        "errors", ex.getErrors(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE DUPLICATE RESOURCE
    // =====================================================
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateException(
            DuplicateResourceException ex) {

        log.warn("Duplicate resource: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE BUSINESS EXCEPTION
    // =====================================================
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            BusinessException ex) {

        log.warn("Business error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE RESOURCE NOT FOUND
    // =====================================================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        log.warn("Resource not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE DATABASE INTEGRITY VIOLATIONS
    // =====================================================
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        log.error("Database integrity violation: {}", ex.getMessage());

        String errorMsg = ex.getMessage();
        Map<String, String> errors = new HashMap<>();
        String message = "Database constraint violation";

        if (errorMsg != null) {
            if (errorMsg.contains("uk_role_tenant_name")) {
                errors.put("name", "Role name already exists for this tenant");
                message = "Validation failed: Role name already exists";
            } else if (errorMsg.contains("uk_department_name_tenant")) {
                errors.put("name", "Department name already exists for this tenant");
                message = "Validation failed: Department name already exists";
            } else if (errorMsg.contains("uk_department_code_tenant")) {
                errors.put("code", "Department code already exists for this tenant");
                message = "Validation failed: Department code already exists";
            } else if (errorMsg.contains("uk_tenant_code")) {
                errors.put("companyName", "This company name / tenant code already exists");
                message = "Validation failed: Company name already registered";
            } else if (errorMsg.contains("email") || errorMsg.contains("uk_employees_email") || errorMsg.contains("uk_employee_email") || errorMsg.contains("uk_platform_user_email")) {
                errors.put("email", "This email address is already registered");
                errors.put("adminEmail", "This email address is already registered");
                message = "Validation failed: Email already registered";
            }
        }

        if (!errors.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", message + " | Raw: " + errorMsg,
                            "errors", errors,
                            "timestamp", LocalDateTime.now()
                    ));
        }

        if (errorMsg != null && errorMsg.contains("unique constraint")) {
            message = "A record with this information already exists";
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "success", false,
                        "message", message,
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE MISSING PARAMETERS
    // =====================================================
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(
            MissingServletRequestParameterException ex) {

        String parameterName = ex.getParameterName();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Required parameter '" + parameterName + "' is missing",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE TYPE MISMATCH
    // =====================================================
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String parameterName = ex.getName();
        Class<?> reqType = ex.getRequiredType();
        String requiredType = reqType != null ? reqType.getSimpleName() : "unknown";

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Parameter '" + parameterName + "' should be of type " + requiredType,
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE MALFORMED JSON
    // =====================================================
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJson(
            HttpMessageNotReadableException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", "Invalid request format. Please check your JSON syntax.",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE ILLEGAL ARGUMENT
    // =====================================================
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE ACCESS DENIED
    // =====================================================
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex) {

        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "success", false,
                        "message", "Access denied: You do not have permission to perform this action",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE BASE EXCEPTIONS (Custom Exceptions)
    // =====================================================
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleBaseException(BaseException ex) {
        log.warn("Custom exception: errorCode={}, statusCode={}, message={}",
                ex.getErrorCode(), ex.getStatusCode(), ex.getMessage());

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "success", false,
                        "errorCode", ex.getErrorCode(),
                        "message", ex.getUserMessage() != null ? ex.getUserMessage() : ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE RESPONSE STATUS EXCEPTIONS
    // =====================================================
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex) {

        log.warn("Response status exception: status={}, reason={}", ex.getStatusCode(), ex.getReason());

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "success", false,
                        "message", ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HANDLE ALL OTHER EXCEPTIONS (Fallback)
    // =====================================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        // Log the full error for debugging
        log.error("Unexpected error occurred", ex);

        // Return user-friendly message without exposing internals
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "message", "Something went wrong. Please try again later.",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private String getFriendlyMessage(String fieldName, String defaultMessage) {
        Map<String, String> friendlyMessages = Map.of(
                "adminEmail", "Please enter a valid email address (e.g., name@company.com)",
                "companyName", "Company name is required",
                "adminName", "Admin name is required",
                "adminPhone", "Please enter a valid phone number",
                "planType", "Please select a valid plan type",
                "email", "Please enter a valid email address",
                "password", "Password is required",
                "confirmPassword", "Passwords do not match"
        );

        return friendlyMessages.getOrDefault(fieldName, defaultMessage);
    }
}