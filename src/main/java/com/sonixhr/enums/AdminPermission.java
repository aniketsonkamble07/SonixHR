package com.sonixhr.enums;

public enum AdminPermission {
    // Tenant Management
    VIEW_TENANTS("View all tenants"),
    CREATE_TENANT("Create new tenant"),
    EDIT_TENANT("Edit tenant details"),
    SUSPEND_TENANT("Suspend tenant"),
    ACTIVATE_TENANT("Activate tenant"),
    DELETE_TENANT("Delete tenant"),
    VIEW_TENANT_DETAILS("View tenant details"),
    MANAGE_TENANT_PLANS("Manage tenant subscription plans"),

    // Admin Management (These were missing)
    MANAGE_ADMINS("Manage admin users"),
    VIEW_ADMINS("View admin users"),
    CREATE_ADMIN("Create new admin"),
    EDIT_ADMIN("Edit admin details"),
    DELETE_ADMIN("Delete admin"),
    MANAGE_ADMIN_ROLES("Manage admin roles and permissions"),
    RESET_ADMIN_PASSWORD("Reset admin password"),

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

    // Security
    VIEW_SECURITY_SETTINGS("View security settings"),
    MANAGE_SECURITY_SETTINGS("Manage security settings"),
    VIEW_SECURITY_REPORTS("View security reports"),
    MANAGE_API_KEYS("Manage API keys"),
    MANAGE_WEBHOOKS("Manage webhooks");

    private final String description;

    AdminPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}