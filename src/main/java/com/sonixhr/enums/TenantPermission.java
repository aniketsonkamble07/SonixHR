package com.sonixhr.enums;

public enum TenantPermission {
    // Employee Management
    EMPLOYEE_VIEW_SELF("View own employee profile"),
    EMPLOYEE_VIEW_TEAM("View team members' profiles"),
    EMPLOYEE_VIEW_ALL("View all employees in tenant"),
    EMPLOYEE_CREATE("Create new employee records"),
    EMPLOYEE_EDIT("Edit employee information"),
    EMPLOYEE_DELETE("Delete employee records"),
    EMPLOYEE_EXPORT("Export employee data"),

    // Leave Management
    LEAVE_REQUEST("Request leave"),
    LEAVE_VIEW_OWN("View own leave requests"),
    LEAVE_VIEW_TEAM("View team leave requests"),
    LEAVE_VIEW_ALL("View all leave requests"),
    LEAVE_APPROVE_DEPARTMENT("Approve leave for own department"),
    LEAVE_APPROVE_ANY("Approve any leave request"),
    LEAVE_CANCEL_OWN("Cancel own leave request"),
    LEAVE_CANCEL_ANY("Cancel any leave request"),

    // Attendance Management
    ATTENDANCE_MARK_SELF("Mark own attendance"),
    ATTENDANCE_MARK_TEAM("Mark attendance for team members"),
    ATTENDANCE_VIEW_OWN("View own attendance history"),
    ATTENDANCE_VIEW_TEAM("View team attendance"),
    ATTENDANCE_VIEW_ALL("View all attendance records"),
    ATTENDANCE_EDIT("Edit attendance records"),
    ATTENDANCE_EXPORT("Export attendance reports"),

    // Task Management
    TASK_CREATE("Create tasks"),
    TASK_VIEW_OWN("View own tasks"),
    TASK_VIEW_TEAM("View team tasks"),
    TASK_VIEW_ALL("View all tasks"),
    TASK_EDIT("Edit tasks"),
    TASK_DELETE("Delete tasks"),
    TASK_ASSIGN("Assign tasks to others"),
    TASK_COMPLETE("Mark tasks as complete"),

    // Report Management
    REPORT_VIEW_DEPARTMENT("View department reports"),
    REPORT_VIEW_COMPANY("View company-wide reports"),
    REPORT_EXPORT("Export reports"),
    REPORT_SCHEDULE("Schedule automated reports"),

    // Settings & Configuration
    SETTINGS_VIEW("View tenant settings"),
    SETTINGS_EDIT("Edit tenant settings"),
    SETTINGS_INTEGRATIONS("Manage integrations"),

    // Role Management
    ROLE_VIEW("View roles"),
    ROLE_CREATE("Create roles"),
    ROLE_EDIT("Edit roles"),
    ROLE_DELETE("Delete roles"),
    ROLE_ASSIGN("Assign roles to users"),

    // Billing (Tenant level)
    VIEW_BILLING("View billing information"),
    MANAGE_SUBSCRIPTION("Manage subscription"),
    VIEW_INVOICES("View invoices"),

    // Department Management
    DEPARTMENT_VIEW("View departments"),
    DEPARTMENT_CREATE("Create departments"),
    DEPARTMENT_EDIT("Edit departments"),
    DEPARTMENT_DELETE("Delete departments");

    private final String description;

    TenantPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}