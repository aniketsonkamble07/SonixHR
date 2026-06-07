package com.sonixhr.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformPermissionEnum {

    // =====================================================
    // TENANT MANAGEMENT
    // =====================================================
    VIEW_TENANTS("View all tenants", "Tenant Management", 1),
    CREATE_TENANT("Create new tenant", "Tenant Management", 2),
    EDIT_TENANT("Edit tenant details", "Tenant Management", 3),
    DELETE_TENANT("Delete tenant", "Tenant Management", 4),
    ACTIVATE_TENANT("Activate tenant", "Tenant Management", 5),
    SUSPEND_TENANT("Suspend tenant", "Tenant Management", 6),
    VIEW_TENANT_DETAILS("View tenant details", "Tenant Management", 7),
    MANAGE_TENANT_PLANS("Manage tenant subscription plans", "Tenant Management", 8),

    // =====================================================
    // PLATFORM ADMIN MANAGEMENT
    // =====================================================
    VIEW_PLATFORM_ADMINS("View platform admin users", "Admin Management", 1),
    CREATE_PLATFORM_ADMIN("Create new platform admin", "Admin Management", 2),
    EDIT_PLATFORM_ADMIN("Edit platform admin details", "Admin Management", 3),
    DELETE_PLATFORM_ADMIN("Delete platform admin", "Admin Management", 4),
    MANAGE_PLATFORM_ADMIN_ROLES("Manage platform admin roles", "Admin Management", 5),
    RESET_PLATFORM_ADMIN_PASSWORD("Reset platform admin password", "Admin Management", 6),

    // =====================================================
    // PLATFORM ROLES
    // =====================================================
    VIEW_PLATFORM_ROLES("View platform roles", "Role Management", 1),
    CREATE_PLATFORM_ROLE("Create platform role", "Role Management", 2),
    EDIT_PLATFORM_ROLE("Edit platform role", "Role Management", 3),
    DELETE_PLATFORM_ROLE("Delete platform role", "Role Management", 4),
    ASSIGN_PLATFORM_ROLE("Assign platform role to users", "Role Management", 5),
    VIEW_PLATFORM_USERS("View platform users", "User Management", 1),
    // =====================================================
    // SYSTEM MANAGEMENT
    // =====================================================
    VIEW_SYSTEM_SETTINGS("View system settings", "System Management", 1),
    MANAGE_SYSTEM_SETTINGS("Manage system settings", "System Management", 2),
    VIEW_SYSTEM_METRICS("View system metrics", "System Management", 3),
    MANAGE_BACKUP("Manage system backup", "System Management", 4),
    VIEW_AUDIT_LOGS("View audit logs", "System Management", 5),
    EXPORT_AUDIT_LOGS("Export audit logs", "System Management", 6),

    // =====================================================
    // BILLING & SUBSCRIPTION
    // =====================================================
    VIEW_SUBSCRIPTIONS("View subscriptions", "Billing", 1),
    MANAGE_SUBSCRIPTIONS("Manage subscriptions", "Billing", 2),
    VIEW_INVOICES("View invoices", "Billing", 3),
    PROCESS_PAYMENTS("Process payments", "Billing", 4),
    VIEW_BILLING_REPORTS("View billing reports", "Billing", 5),
    MANAGE_PRICING_PLANS("Manage pricing plans", "Billing", 6),

    // =====================================================
    // SUPPORT
    // =====================================================
    VIEW_SUPPORT_TICKETS("View support tickets", "Support", 1),
    MANAGE_SUPPORT_TICKETS("Manage support tickets", "Support", 2),
    RESOLVE_ISSUES("Resolve issues", "Support", 3),
    VIEW_SUPPORT_METRICS("View support metrics", "Support", 4),

    // =====================================================
    // ANALYTICS & REPORTS
    // =====================================================
    VIEW_ANALYTICS("View analytics", "Analytics", 1),
    EXPORT_REPORTS("Export reports", "Analytics", 2),
    VIEW_SYSTEM_HEALTH("View system health", "Analytics", 3),
    VIEW_ACTIVITY_REPORTS("View activity reports", "Analytics", 4),

    // =====================================================
    // SECURITY
    // =====================================================
    VIEW_SECURITY_SETTINGS("View security settings", "Security", 1),
    MANAGE_SECURITY_SETTINGS("Manage security settings", "Security", 2),
    VIEW_SECURITY_REPORTS("View security reports", "Security", 3),
    MANAGE_API_KEYS("Manage API keys", "Security", 4),
    MANAGE_WEBHOOKS("Manage webhooks", "Security", 5);

    private final String description;
    private final String category;
    private final int order;
}