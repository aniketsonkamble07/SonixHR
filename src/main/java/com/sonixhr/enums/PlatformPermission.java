package com.sonixhr.enums;
public enum PlatformPermission {
    // Tenant Management (Platform level)
    VIEW_TENANTS("View all tenants"),
    CREATE_TENANT("Create new tenant"),
    EDIT_TENANT("Edit tenant details"),
    SUSPEND_TENANT("Suspend tenant"),
    ACTIVATE_TENANT("Activate tenant"),
    DELETE_TENANT("Delete tenant"),
    VIEW_TENANT_DETAILS("View tenant details"),
    MANAGE_TENANT_PLANS("Manage tenant subscription plans"),

    // Platform Admin Management
    MANAGE_PLATFORM_ADMINS("Manage platform admin users"),
    VIEW_PLATFORM_ADMINS("View platform admin users"),
    CREATE_PLATFORM_ADMIN("Create new platform admin"),
    EDIT_PLATFORM_ADMIN("Edit platform admin details"),
    DELETE_PLATFORM_ADMIN("Delete platform admin"),
    MANAGE_PLATFORM_ADMIN_ROLES("Manage platform admin roles"),
    RESET_PLATFORM_ADMIN_PASSWORD("Reset platform admin password"),

    // System Management
    VIEW_SYSTEM_SETTINGS("View system settings"),
    MANAGE_SYSTEM_SETTINGS("Manage system settings"),
    VIEW_SYSTEM_METRICS("View system metrics"),
    MANAGE_BACKUP("Manage system backup"),
    VIEW_AUDIT_LOGS("View audit logs"),
    EXPORT_AUDIT_LOGS("Export audit logs"),

    // Billing & Subscription
    VIEW_SUBSCRIPTIONS("View subscriptions"),
    MANAGE_SUBSCRIPTIONS("Manage subscriptions"),
    VIEW_INVOICES("View invoices"),
    PROCESS_PAYMENTS("Process payments"),
    VIEW_BILLING_REPORTS("View billing reports"),
    MANAGE_PRICING_PLANS("Manage pricing plans"),

    // Support
    VIEW_SUPPORT_TICKETS("View support tickets"),
    MANAGE_SUPPORT_TICKETS("Manage support tickets"),
    RESOLVE_ISSUES("Resolve issues"),
    VIEW_SUPPORT_METRICS("View support metrics"),

    // Analytics & Reports
    VIEW_ANALYTICS("View analytics"),
    EXPORT_REPORTS("Export reports"),
    VIEW_SYSTEM_HEALTH("View system health"),
    VIEW_ACTIVITY_REPORTS("View activity reports"),

    // Platform Security
    VIEW_SECURITY_SETTINGS("View security settings"),
    MANAGE_SECURITY_SETTINGS("Manage security settings"),
    VIEW_SECURITY_REPORTS("View security reports"),
    MANAGE_API_KEYS("Manage API keys"),
    MANAGE_WEBHOOKS("Manage webhooks"),

    // Platform Roles
    MANAGE_PLATFORM_ROLES("Manage platform roles"),
    VIEW_PLATFORM_ROLES("View platform roles");

    private final String description;

    PlatformPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}