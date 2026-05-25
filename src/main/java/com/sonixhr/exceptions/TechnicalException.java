package com.sonixhr.exceptions;

public class TechnicalException extends BaseException {

    public TechnicalException(String message) {
        super("TECH_001", 500, "A technical error occurred", message);
    }

    public TechnicalException(String message, Throwable cause) {
        super("TECH_001", 500, "A technical error occurred", message, cause);
    }

    public TechnicalException(String errorCode, String userMessage, String technicalMessage) {
        super(errorCode, 500, userMessage, technicalMessage);
    }
}