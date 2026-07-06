package com.sonixhr.exceptions;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

@Getter
public class ValidationException extends BaseException {
    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("VAL_001", 400, "Validation failed", "Validation failed");
        this.errors = errors;
    }

    public ValidationException(String fieldName, String message) {
        super("VAL_001", 400, "Validation failed", "Validation failed for field: " + fieldName);
        this.errors = new HashMap<>();
        this.errors.put(fieldName, message);
    }
}
