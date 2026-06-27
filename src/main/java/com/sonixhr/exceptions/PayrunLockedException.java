package com.sonixhr.exceptions;

public class PayrunLockedException extends BaseException {

    public PayrunLockedException(String message) {
        super("PAYRUN_LOCKED", 409, message, message);
    }
}
