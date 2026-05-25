package com.sonixhr.exceptions;

public class BusinessException extends BaseException {

    public BusinessException(String message) {
        super("BUS_001", 400, message, message);
    }

    public BusinessException(String errorCode, String message) {
        super(errorCode, 400, message, message);
    }

    public BusinessException(String message, Throwable cause) {
        super("BUS_001", 400, message, message, cause);
    }

    public BusinessException(String errorCode, int statusCode, String userMessage,
                             String technicalMessage) {
        super(errorCode, statusCode, userMessage, technicalMessage);
    }
}