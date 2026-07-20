package com.sonixhr.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TenantPermissionEnum {

    // =====================================================
    // EMPLOYEE MANAGEMENT
    // =====================================================
    EMPLOYEE_VIEW_SELF("View own employee profile", "Employee Management", 1),
    EMPLOYEE_VIEW_TEAM("View team members' profiles", "Employee Management", 2),
    EMPLOYEE_VIEW_ALL("View all employees in tenant", "Employee Management", 3),
    EMPLOYEE_CREATE("Create new employee records", "Employee Management", 4),
    EMPLOYEE_EDIT("Edit employee information", "Employee Management", 5),
    EMPLOYEE_DELETE("Delete employee records", "Employee Management", 6),
    EMPLOYEE_EXPORT("Export employee data", "Employee Management", 7),

    // =====================================================
    // LEAVE MANAGEMENT
    // =====================================================
    LEAVE_REQUEST("Request leave", "Leave Management", 1),
    LEAVE_VIEW_OWN("View own leave requests", "Leave Management", 2),
    LEAVE_VIEW_TEAM("View team leave requests", "Leave Management", 3),
    LEAVE_VIEW_ALL("View all leave requests", "Leave Management", 4),
    LEAVE_APPROVE_DEPARTMENT("Approve leave for own department", "Leave Management", 5),
    LEAVE_APPROVE_ANY("Approve any leave request", "Leave Management", 6),
    LEAVE_CANCEL_OWN("Cancel own leave request", "Leave Management", 7),
    LEAVE_CANCEL_ANY("Cancel any leave request", "Leave Management", 8),

    // =====================================================
    // ATTENDANCE MANAGEMENT
    // =====================================================
    ATTENDANCE_MARK_SELF("Mark own attendance", "Attendance Management", 1),
    ATTENDANCE_MARK_TEAM("Mark attendance for team members", "Attendance Management", 2),
    ATTENDANCE_VIEW_OWN("View own attendance history", "Attendance Management", 3),
    ATTENDANCE_VIEW_TEAM("View team attendance", "Attendance Management", 4),
    ATTENDANCE_VIEW_ALL("View all attendance records", "Attendance Management", 5),
    ATTENDANCE_EDIT("Edit attendance records", "Attendance Management", 6),
    ATTENDANCE_EXPORT("Export attendance reports", "Attendance Management", 7),
    SHIFT_CREATE("Create shift configurations", "Attendance Management", 8),
    SHIFT_UPDATE("Update shift configurations", "Attendance Management", 9),
    SHIFT_VIEW("View shift configurations", "Attendance Management", 10),
    SHIFT_VIEW_ALL("View all shift configurations", "Attendance Management", 11),
    SHIFT_ADMIN("Manage shifts", "Attendance Management", 12),
    SHIFT_DELETE("Delete shift configurations", "Attendance Management", 13),
    SHIFT_HARD_DELETE("Hard delete shift configurations", "Attendance Management", 16),
    ATTENDANCE_MARK("Mark attendance", "Attendance Management", 14),
    ATTENDANCE_VIEW("View attendance logs", "Attendance Management", 15),

    // =====================================================
    // DEPARTMENT MANAGEMENT
    // =====================================================
    DEPARTMENT_VIEW("View departments", "Department Management", 1),
    DEPARTMENT_CREATE("Create departments", "Department Management", 2),
    DEPARTMENT_EDIT("Edit departments", "Department Management", 3),
    DEPARTMENT_DELETE("Delete departments", "Department Management", 4),

    // =====================================================
    // ROLE MANAGEMENT
    // =====================================================
    ROLE_VIEW("View roles", "Role Management", 1),
    ROLE_CREATE("Create new roles", "Role Management", 2),
    ROLE_EDIT("Edit role details", "Role Management", 3),
    ROLE_DELETE("Delete roles", "Role Management", 4),
    ROLE_ASSIGN("Assign roles to users", "Role Management", 5),
    ROLE_REMOVE("Remove roles from users", "Role Management", 6),
    ROLE_BULK_ASSIGN("Bulk assign roles to multiple users", "Role Management", 7),
    ROLE_BULK_REMOVE("Bulk remove roles from multiple users", "Role Management", 8),
    ROLE_SET_DEFAULT("Set default role for new employees", "Role Management", 9),
    ROLE_VIEW_PERMISSIONS("View permissions of a role", "Role Management", 10),
    ROLE_EDIT_PERMISSIONS("Edit permissions of a role", "Role Management", 11),
    ROLE_COPY("Copy existing role", "Role Management", 12),
    ROLE_DUPLICATE("Duplicate role", "Role Management", 13),
    ROLE_EXPORT("Export role definitions", "Role Management", 14),
    ROLE_IMPORT("Import role definitions", "Role Management", 15),

    // =====================================================
    // PERMISSION MANAGEMENT
    // =====================================================
    PERMISSION_VIEW("View all permissions", "Permission Management", 1),
    PERMISSION_VIEW_BY_CATEGORY("View permissions by category", "Permission Management", 2),

    // =====================================================
    // REPORT MANAGEMENT
    // =====================================================
    REPORT_VIEW_DEPARTMENT("View department reports", "Report Management", 1),
    REPORT_VIEW_COMPANY("View company-wide reports", "Report Management", 2),
    REPORT_EXPORT("Export reports", "Report Management", 3),
    REPORT_SCHEDULE("Schedule automated reports", "Report Management", 4),

    // =====================================================
    // SETTINGS
    // =====================================================
    SETTINGS_VIEW("View tenant settings", "Settings", 1),
    SETTINGS_EDIT("Edit tenant settings", "Settings", 2),
    SETTINGS_INTEGRATIONS("Manage integrations", "Settings", 3),

    // =====================================================
    // BILLING
    // =====================================================
    VIEW_BILLING("View billing information", "Billing", 1),
    MANAGE_SUBSCRIPTION("Manage subscription", "Billing", 2),
    VIEW_INVOICES("View invoices", "Billing", 3),

    // =====================================================
    // AUDIT & SECURITY
    // =====================================================
    AUDIT_ROLE_ASSIGNMENTS("View role assignment audit logs", "Audit", 1),
    AUDIT_PERMISSION_CHANGES("View permission change audit logs", "Audit", 2),
    API_LOG_VIEW("View API hit logs", "Audit", 3),
    API_LOG_TOGGLE("Toggle API hit logging visibility", "Audit", 4),

    // =====================================================
    // TASK MANAGEMENT
    // =====================================================
    TASK_CREATE("Create and assign tasks", "Task Management", 1),
    TASK_VIEW_ALL("View all tasks in tenant", "Task Management", 2),
    TASK_VIEW_TEAM("View team tasks", "Task Management", 3),
    TASK_VIEW_OWN("View own assigned tasks", "Task Management", 4),
    TASK_EDIT("Edit task details", "Task Management", 5),
    TASK_ACKNOWLEDGE("Acknowledge assigned tasks", "Task Management", 6),
    TASK_UPDATE_STATUS("Update own task status", "Task Management", 7);

    private final String description;
    private final String category;
    private final int order;

    TenantPermissionEnum(String description, String category, int order) {
        this.description = description;
        this.category = category;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getOrder() {
        return order;
    }
}