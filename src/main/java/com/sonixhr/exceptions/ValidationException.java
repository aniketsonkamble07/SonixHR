package com.sonixhr.exceptions;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

@Getter
public class ValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public ValidationException(String fieldName, String message) {
        super("Validation failed for field: " + fieldName);
        this.errors = new HashMap<>();
        this.errors.put(fieldName, message);
    }
}
