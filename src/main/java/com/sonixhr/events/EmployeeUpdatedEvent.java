// com.sonixhr.events.EmployeeUpdatedEvent.java
package com.sonixhr.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EmployeeUpdatedEvent {
    private final String email;
    private final Long employeeId;
    private final String action; // "DEACTIVATE", "ACTIVATE", "ROLE_CHANGE", "UPDATE"

    public static EmployeeUpdatedEvent deactivated(String email, Long employeeId) {
        return new EmployeeUpdatedEvent(email, employeeId, "DEACTIVATE");
    }

    public static EmployeeUpdatedEvent activated(String email, Long employeeId) {
        return new EmployeeUpdatedEvent(email, employeeId, "ACTIVATE");
    }

    public static EmployeeUpdatedEvent rolesChanged(String email, Long employeeId) {
        return new EmployeeUpdatedEvent(email, employeeId, "ROLE_CHANGE");
    }
}