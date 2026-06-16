package com.sonixhr.exceptions;

public class LeavePoliciesNotConfiguredException extends BusinessException {

    public LeavePoliciesNotConfiguredException(String message) {
        super("LEAVE_001", message);
    }
}
