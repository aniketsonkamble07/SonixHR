package com.sonixhr.dto.department;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DepartmentResponse {

    // Basic Department Information
    private Long id;
    private UUID tenantId;
    private String name;
    private String code;
    private String description;

    // Employee Counts
    private Long totalEmployees;      // Total employees in department (including all statuses)
    private Long activeEmployees;     // Active employees only (ACTIVE status)
    private Long onProbationEmployees; // Employees on probation
    private Long onLeaveEmployees;     // Employees on leave
    private Long resignedEmployees;    // Resigned employees

    // Optional: Percentage calculations
    private Double activePercentage;   // Percentage of active employees
    private Double probationPercentage; // Percentage of employees on probation

    // Manager Information
    private Long managerId;
    private String managerName;
    private String managerEmail;

    // Budget Information
    private Double annualBudget;
    private Double actualSpent;
    private Double remainingBudget;

    // Dates
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}