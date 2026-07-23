package com.sonixhr.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum TenantPermissionEnum {

    // =====================================================
    // EMPLOYEE MANAGEMENT
    // =====================================================
    EMPLOYEE_VIEW_SELF("View own employee profile", "Employee Management", 1, PermissionType.DATA_VIEW),
    EMPLOYEE_VIEW_TEAM("View team members' profiles", "Employee Management", 2, PermissionType.DATA_VIEW),
    EMPLOYEE_VIEW_ALL("View all employees in tenant", "Employee Management", 3, PermissionType.DATA_VIEW),
    EMPLOYEE_CREATE("Create new employee records", "Employee Management", 4, PermissionType.DATA_CREATE),
    EMPLOYEE_EDIT("Edit employee information", "Employee Management", 5, PermissionType.DATA_EDIT),
    EMPLOYEE_DELETE("Delete employee records", "Employee Management", 6, PermissionType.DATA_DELETE),
    EMPLOYEE_EXPORT("Export employee data", "Employee Management", 7, PermissionType.DATA_EXPORT),

    // =====================================================
    // LEAVE MANAGEMENT
    // =====================================================
    LEAVE_REQUEST("Request leave", "Leave Management", 1, PermissionType.DATA_CREATE),
    LEAVE_VIEW_OWN("View own leave requests", "Leave Management", 2, PermissionType.DATA_VIEW),
    LEAVE_VIEW_TEAM("View team leave requests", "Leave Management", 3, PermissionType.DATA_VIEW),
    LEAVE_VIEW_ALL("View all leave requests", "Leave Management", 4, PermissionType.DATA_VIEW),
    LEAVE_APPROVE_DEPARTMENT("Approve leave for own department", "Leave Management", 5, PermissionType.DATA_APPROVE),
    LEAVE_APPROVE_ANY("Approve any leave request", "Leave Management", 6, PermissionType.DATA_APPROVE),
    LEAVE_CANCEL_OWN("Cancel own leave request", "Leave Management", 7, PermissionType.DATA_DELETE),
    LEAVE_CANCEL_ANY("Cancel any leave request", "Leave Management", 8, PermissionType.DATA_DELETE),

    // =====================================================
    // ATTENDANCE MANAGEMENT
    // =====================================================
    ATTENDANCE_MARK_SELF("Mark own attendance", "Attendance Management", 1, PermissionType.DATA_CREATE),
    ATTENDANCE_MARK_TEAM("Mark attendance for team members", "Attendance Management", 2, PermissionType.DATA_CREATE),
    ATTENDANCE_VIEW_OWN("View own attendance history", "Attendance Management", 3, PermissionType.DATA_VIEW),
    ATTENDANCE_VIEW_TEAM("View team attendance", "Attendance Management", 4, PermissionType.DATA_VIEW),
    ATTENDANCE_VIEW_ALL("View all attendance records", "Attendance Management", 5, PermissionType.DATA_VIEW),
    ATTENDANCE_EDIT("Edit attendance records", "Attendance Management", 6, PermissionType.DATA_EDIT),
    ATTENDANCE_EXPORT("Export attendance reports", "Attendance Management", 7, PermissionType.DATA_EXPORT),
    SHIFT_CREATE("Create shift configurations", "Attendance Management", 8, PermissionType.DATA_CREATE),
    SHIFT_UPDATE("Update shift configurations", "Attendance Management", 9, PermissionType.DATA_EDIT),
    SHIFT_VIEW("View shift configurations", "Attendance Management", 10, PermissionType.DATA_VIEW),
    SHIFT_VIEW_ALL("View all shift configurations", "Attendance Management", 11, PermissionType.DATA_VIEW),
    SHIFT_ADMIN("Manage shifts", "Attendance Management", 12, PermissionType.DATA_ADMIN),
    SHIFT_DELETE("Delete shift configurations", "Attendance Management", 13, PermissionType.DATA_DELETE),
    SHIFT_HARD_DELETE("Hard delete shift configurations", "Attendance Management", 16, PermissionType.DATA_DELETE),
    ATTENDANCE_MARK("Mark attendance", "Attendance Management", 14, PermissionType.DATA_CREATE),
    ATTENDANCE_VIEW("View attendance logs", "Attendance Management", 15, PermissionType.DATA_VIEW),

    // =====================================================
    // DEPARTMENT MANAGEMENT
    // =====================================================
    DEPARTMENT_VIEW("View departments", "Department Management", 1, PermissionType.DATA_VIEW),
    DEPARTMENT_CREATE("Create departments", "Department Management", 2, PermissionType.DATA_CREATE),
    DEPARTMENT_EDIT("Edit departments", "Department Management", 3, PermissionType.DATA_EDIT),
    DEPARTMENT_DELETE("Delete departments", "Department Management", 4, PermissionType.DATA_DELETE),

    // =====================================================
    // ROLE MANAGEMENT
    // =====================================================
    ROLE_VIEW("View roles", "Role Management", 1, PermissionType.DATA_VIEW),
    ROLE_CREATE("Create new roles", "Role Management", 2, PermissionType.DATA_CREATE),
    ROLE_EDIT("Edit role details", "Role Management", 3, PermissionType.DATA_EDIT),
    ROLE_DELETE("Delete roles", "Role Management", 4, PermissionType.DATA_DELETE),
    ROLE_ASSIGN("Assign roles to users", "Role Management", 5, PermissionType.DATA_EDIT),
    ROLE_REMOVE("Remove roles from users", "Role Management", 6, PermissionType.DATA_EDIT),
    ROLE_BULK_ASSIGN("Bulk assign roles to multiple users", "Role Management", 7, PermissionType.DATA_EDIT),
    ROLE_BULK_REMOVE("Bulk remove roles from multiple users", "Role Management", 8, PermissionType.DATA_EDIT),
    ROLE_SET_DEFAULT("Set default role for new employees", "Role Management", 9, PermissionType.DATA_EDIT),
    ROLE_VIEW_PERMISSIONS("View permissions of a role", "Role Management", 10, PermissionType.DATA_VIEW),
    ROLE_EDIT_PERMISSIONS("Edit permissions of a role", "Role Management", 11, PermissionType.DATA_EDIT),
    ROLE_COPY("Copy existing role", "Role Management", 12, PermissionType.DATA_CREATE),
    ROLE_DUPLICATE("Duplicate role", "Role Management", 13, PermissionType.DATA_CREATE),
    ROLE_EXPORT("Export role definitions", "Role Management", 14, PermissionType.DATA_EXPORT),
    ROLE_IMPORT("Import role definitions", "Role Management", 15, PermissionType.DATA_IMPORT),

    // =====================================================
    // PERMISSION MANAGEMENT
    // =====================================================
    PERMISSION_VIEW("View all permissions", "Permission Management", 1, PermissionType.DATA_VIEW),
    PERMISSION_VIEW_BY_CATEGORY("View permissions by category", "Permission Management", 2, PermissionType.DATA_VIEW),

    // =====================================================
    // REPORT MANAGEMENT
    // =====================================================
    REPORT_VIEW_DEPARTMENT("View department reports", "Report Management", 1, PermissionType.DATA_VIEW),
    REPORT_VIEW_COMPANY("View company-wide reports", "Report Management", 2, PermissionType.DATA_VIEW),
    REPORT_EXPORT("Export reports", "Report Management", 3, PermissionType.DATA_EXPORT),
    REPORT_SCHEDULE("Schedule automated reports", "Report Management", 4, PermissionType.DATA_CREATE),

    // =====================================================
    // SETTINGS
    // =====================================================
    SETTINGS_VIEW("View tenant settings", "Settings", 1, PermissionType.DATA_VIEW),
    SETTINGS_EDIT("Edit tenant settings", "Settings", 2, PermissionType.DATA_EDIT),
    SETTINGS_INTEGRATIONS("Manage integrations", "Settings", 3, PermissionType.DATA_ADMIN),

    // =====================================================
    // BILLING (ALLOWED FOR EXPIRED TENANTS)
    // =====================================================
    VIEW_BILLING("View billing information", "Billing", 1, PermissionType.DATA_VIEW),
    MANAGE_SUBSCRIPTION("Manage subscription", "Billing", 2, PermissionType.DATA_ADMIN),
    VIEW_INVOICES("View invoices", "Billing", 3, PermissionType.DATA_VIEW),

    // =====================================================
    // AUDIT & SECURITY
    // =====================================================
    AUDIT_ROLE_ASSIGNMENTS("View role assignment audit logs", "Audit", 1, PermissionType.DATA_VIEW),
    AUDIT_PERMISSION_CHANGES("View permission change audit logs", "Audit", 2, PermissionType.DATA_VIEW),
    API_LOG_VIEW("View API hit logs", "Audit", 3, PermissionType.DATA_VIEW),
    API_LOG_TOGGLE("Toggle API hit logging visibility", "Audit", 4, PermissionType.DATA_ADMIN),

    // =====================================================
    // TASK MANAGEMENT
    // =====================================================
    TASK_CREATE("Create and assign tasks", "Task Management", 1, PermissionType.DATA_CREATE),
    TASK_VIEW_ALL("View all tasks in tenant", "Task Management", 2, PermissionType.DATA_VIEW),
    TASK_VIEW_TEAM("View team tasks", "Task Management", 3, PermissionType.DATA_VIEW),
    TASK_VIEW_OWN("View own assigned tasks", "Task Management", 4, PermissionType.DATA_VIEW),
    TASK_EDIT("Edit task details", "Task Management", 5, PermissionType.DATA_EDIT),
    TASK_ACKNOWLEDGE("Acknowledge assigned tasks", "Task Management", 6, PermissionType.DATA_EDIT),
    TASK_UPDATE_STATUS("Update own task status", "Task Management", 7, PermissionType.DATA_EDIT);

    private final String description;
    private final String category;
    private final int order;
    private final PermissionType type;

    /**
     * Permission types for categorization
     */
    public enum PermissionType {
        DATA_VIEW("View data"),
        DATA_CREATE("Create data"),
        DATA_EDIT("Edit data"),
        DATA_DELETE("Delete data"),
        DATA_APPROVE("Approve data"),
        DATA_EXPORT("Export data"),
        DATA_IMPORT("Import data"),
        DATA_ADMIN("Admin data");

        private final String displayName;

        PermissionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Check if this permission is a billing-related permission
     * These are the only permissions accessible to expired tenants
     */
    public boolean isBillingPermission() {
        return this == VIEW_BILLING ||
                this == MANAGE_SUBSCRIPTION ||
                this == VIEW_INVOICES;
    }

    /**
     * Check if this permission should be accessible to expired tenants
     */
    public boolean isExpiredTenantAccessible() {
        return this.isBillingPermission();
    }

    /**
     * Check if this is a platform admin level permission
     */
    public boolean isPlatformAdminPermission() {
        return this.category.equals("Audit") ||
                this.category.equals("Permission Management") ||
                this == API_LOG_VIEW ||
                this == API_LOG_TOGGLE ||
                this == AUDIT_ROLE_ASSIGNMENTS ||
                this == AUDIT_PERMISSION_CHANGES ||
                this == SETTINGS_VIEW ||
                this == SETTINGS_EDIT ||
                this == SETTINGS_INTEGRATIONS ||
                this == PERMISSION_VIEW ||
                this == PERMISSION_VIEW_BY_CATEGORY;
    }

    /**
     * Check if this permission is a view-only permission
     */
    public boolean isViewPermission() {
        return this.type == PermissionType.DATA_VIEW;
    }

    /**
     * Check if this permission is a write permission
     */
    public boolean isWritePermission() {
        return this.type == PermissionType.DATA_CREATE ||
                this.type == PermissionType.DATA_EDIT ||
                this.type == PermissionType.DATA_DELETE ||
                this.type == PermissionType.DATA_APPROVE ||
                this.type == PermissionType.DATA_ADMIN;
    }

    /**
     * Check if this permission is an export permission
     */
    public boolean isExportPermission() {
        return this.type == PermissionType.DATA_EXPORT ||
                this.type == PermissionType.DATA_IMPORT;
    }

    /**
     * Get all permissions by category
     */
    public static List<TenantPermissionEnum> getByCategory(String category) {
        return Arrays.stream(values())
                .filter(p -> p.getCategory().equals(category))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all billing permissions
     */
    public static List<TenantPermissionEnum> getBillingPermissions() {
        return Arrays.stream(values())
                .filter(TenantPermissionEnum::isBillingPermission)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all platform admin permissions
     */
    public static List<TenantPermissionEnum> getPlatformAdminPermissions() {
        return Arrays.stream(values())
                .filter(TenantPermissionEnum::isPlatformAdminPermission)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all view permissions
     */
    public static List<TenantPermissionEnum> getViewPermissions() {
        return Arrays.stream(values())
                .filter(TenantPermissionEnum::isViewPermission)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all write permissions
     */
    public static List<TenantPermissionEnum> getWritePermissions() {
        return Arrays.stream(values())
                .filter(TenantPermissionEnum::isWritePermission)
                .collect(java.util.stream.Collectors.toList());
    }
}