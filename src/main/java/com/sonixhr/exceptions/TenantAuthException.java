package com.sonixhr.exceptions;

public class TenantAuthException extends BaseException {

    public TenantAuthException(String message) {
        super("AUTH_001", 401, message, message);
    }

    public TenantAuthException(String errorCode, String message) {
        super(errorCode, 401, message, message);
    }

    public TenantAuthException(String message, Throwable cause) {
        super("AUTH_001", 401, message, message, cause);
    }

    public TenantAuthException(String errorCode, String message, Throwable cause) {
        super(errorCode, 401, message, message, cause);
    }
}
