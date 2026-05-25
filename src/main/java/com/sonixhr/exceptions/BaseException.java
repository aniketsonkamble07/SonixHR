package com.sonixhr.exceptions;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final int statusCode;
    private final String userMessage;
    private final String technicalMessage;
    private final transient Object[] args;

    protected BaseException(String errorCode, int statusCode, String userMessage,
                            String technicalMessage, Object... args) {
        super(technicalMessage);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.userMessage = userMessage;
        this.technicalMessage = technicalMessage;
        this.args = args;
    }

    protected BaseException(String errorCode, int statusCode, String userMessage,
                            String technicalMessage, Throwable cause, Object... args) {
        super(technicalMessage, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.userMessage = userMessage;
        this.technicalMessage = technicalMessage;
        this.args = args;
    }
}