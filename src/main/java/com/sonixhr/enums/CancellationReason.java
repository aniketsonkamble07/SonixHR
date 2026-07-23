package com.sonixhr.enums;

public enum CancellationReason {
    USER_REQUESTED("User requested cancellation"),
    EXPIRED("Subscription expired"),
    PAYMENT_FAILED("Payment failed"),
    UPGRADED("Upgraded to higher plan"),
    DOWNGRADED("Downgraded to lower plan"),
    TENANT_CLOSED("Tenant closed"),
    ADMIN_ACTION("Admin action"),
    FRAUD_PREVENTION("Fraud prevention"),
    CUSTOMER_INITIATED("Customer initiated"),
    COST("Cost too high"),
    NOT_USING("Not using the service"),
    OTHER("Other reasons");

    private final String description;

    CancellationReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}