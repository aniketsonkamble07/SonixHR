package com.sonixhr.enums.employee;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmployeePermissionEnum {

    // =====================================================
    // SELF MANAGEMENT (Basic employee rights)
    // =====================================================
    VIEW_OWN_PROFILE("View own employee profile", "Self Management", 1),
    EDIT_OWN_PROFILE("Edit own profile information", "Self Management", 2),
    VIEW_OWN_ATTENDANCE("View own attendance history", "Self Management", 3),
    MARK_OWN_ATTENDANCE("Mark own attendance (check-in/out)", "Self Management", 4),
    APPLY_OWN_LEAVE("Apply for own leave", "Self Management", 5),
    VIEW_OWN_LEAVE_BALANCE("View own leave balance", "Self Management", 6),
    CANCEL_OWN_LEAVE("Cancel own leave request", "Self Management", 7),
    VIEW_OWN_SALARY_SLIP("View own salary slip", "Self Management", 8),
    VIEW_OWN_HOLIDAYS("View holiday calendar", "Self Management", 9),
    VIEW_OWN_SHIFT("View assigned shift schedule", "Self Management", 10),

    // =====================================================
    // TEAM MANAGEMENT (For employees with team/manager role)
    // =====================================================
    VIEW_TEAM_MEMBERS("View team members", "Team Management", 1),
    VIEW_TEAM_ATTENDANCE("View team attendance", "Team Management", 2),
    MARK_TEAM_ATTENDANCE("Mark attendance for team members", "Team Management", 3),
    APPROVE_TEAM_LEAVE("Approve leave for team members", "Team Management", 4),
    REJECT_TEAM_LEAVE("Reject leave for team members", "Team Management", 5),
    VIEW_TEAM_LEAVE_REQUESTS("View team leave requests", "Team Management", 6),
    ASSIGN_TASKS_TO_TEAM("Assign tasks to team members", "Team Management", 7),
    VIEW_TEAM_TASKS("View team tasks", "Team Management", 8),

    // =====================================================
    // BASIC OPERATIONS (Common employee actions)
    // =====================================================
    SUBMIT_WORK_REPORT("Submit daily/weekly work report", "Basic Operations", 1),
    VIEW_COMPANY_NEWS("View company news and announcements", "Basic Operations", 2),
    VIEW_POLICIES("View company policies", "Basic Operations", 3),
    REQUEST_RESOURCES("Request resources/equipment", "Basic Operations", 4),
    SUBMIT_EXPENSE_CLAIM("Submit expense claim", "Basic Operations", 5),

    // =====================================================
    // DOCUMENT MANAGEMENT
    // =====================================================
    VIEW_OWN_DOCUMENTS("View own documents", "Document Management", 1),
    UPLOAD_OWN_DOCUMENTS("Upload own documents", "Document Management", 2),
    DELETE_OWN_DOCUMENTS("Delete own documents", "Document Management", 3),

    // =====================================================
    // NOTIFICATIONS
    // =====================================================
    VIEW_NOTIFICATIONS("View notifications", "Notifications", 1),
    MARK_NOTIFICATIONS_READ("Mark notifications as read", "Notifications", 2),
    SUBSCRIBE_TO_UPDATES("Subscribe to updates", "Notifications", 3);

    private final String description;
    private final String category;
    private final int order;
}