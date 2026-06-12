package com.sonixhr.exceptions;

public class TokenValidationException extends BaseException {

    public TokenValidationException(String message) {
        super("AUTH_002", 401, message, message);
    }

    public TokenValidationException(String errorCode, String message) {
        super(errorCode, 401, message, message);
    }

    public TokenValidationException(String message, Throwable cause) {
        super("AUTH_002", 401, message, message, cause);
    }

    public TokenValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, 401, message, message, cause);
    }
}
