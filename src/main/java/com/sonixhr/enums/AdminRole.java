package com.sonixhr.enums;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum AdminRole {
    SUPER_ADMIN("Super Administrator", "Full system access",
            Set.of(AdminPermission.values())),

    TENANT_MANAGER("Tenant Manager", "Manage all tenants",
            Set.of(
                    AdminPermission.VIEW_TENANTS,
                    AdminPermission.CREATE_TENANT,
                    AdminPermission.EDIT_TENANT,
                    AdminPermission.SUSPEND_TENANT,
                    AdminPermission.ACTIVATE_TENANT,
                    AdminPermission.VIEW_TENANT_DETAILS,
                    AdminPermission.MANAGE_TENANT_PLANS,
                    AdminPermission.VIEW_AUDIT_LOGS,
                    AdminPermission.VIEW_SUPPORT_TICKETS
            )),

    ADMIN_MANAGER("Admin Manager", "Manage admin users and roles",
            Set.of(
                    AdminPermission.MANAGE_ADMINS,
                    AdminPermission.VIEW_ADMINS,
                    AdminPermission.CREATE_ADMIN,
                    AdminPermission.EDIT_ADMIN,
                    AdminPermission.DELETE_ADMIN,
                    AdminPermission.MANAGE_ADMIN_ROLES,
                    AdminPermission.RESET_ADMIN_PASSWORD,
                    AdminPermission.VIEW_AUDIT_LOGS
            )),

    SUPPORT_MANAGER("Support Manager", "Manage support tickets and user issues",
            Set.of(
                    AdminPermission.VIEW_TENANTS,
                    AdminPermission.VIEW_TENANT_DETAILS,
                    AdminPermission.VIEW_SUPPORT_TICKETS,
                    AdminPermission.MANAGE_SUPPORT_TICKETS,
                    AdminPermission.RESOLVE_ISSUES,
                    AdminPermission.VIEW_SUPPORT_METRICS,
                    AdminPermission.VIEW_AUDIT_LOGS
            )),

    BILLING_MANAGER("Billing Manager", "Manage subscriptions and invoices",
            Set.of(
                    AdminPermission.VIEW_TENANTS,
                    AdminPermission.VIEW_SUBSCRIPTIONS,
                    AdminPermission.MANAGE_SUBSCRIPTIONS,
                    AdminPermission.VIEW_INVOICES,
                    AdminPermission.PROCESS_PAYMENTS,
                    AdminPermission.VIEW_BILLING_REPORTS,
                    AdminPermission.MANAGE_PRICING_PLANS
            )),

    ANALYTICS_MANAGER("Analytics Manager", "View system analytics and reports",
            Set.of(
                    AdminPermission.VIEW_ANALYTICS,
                    AdminPermission.EXPORT_REPORTS,
                    AdminPermission.VIEW_SYSTEM_METRICS,
                    AdminPermission.VIEW_SYSTEM_HEALTH,
                    AdminPermission.VIEW_ACTIVITY_REPORTS,
                    AdminPermission.VIEW_AUDIT_LOGS
            )),

    SECURITY_ADMIN("Security Administrator", "Manage security settings",
            Set.of(
                    AdminPermission.VIEW_SECURITY_SETTINGS,
                    AdminPermission.MANAGE_SECURITY_SETTINGS,
                    AdminPermission.VIEW_SECURITY_REPORTS,
                    AdminPermission.MANAGE_API_KEYS,
                    AdminPermission.MANAGE_WEBHOOKS,
                    AdminPermission.VIEW_AUDIT_LOGS,
                    AdminPermission.MANAGE_ADMINS
            )),

    SYSTEM_MONITOR("System Monitor", "Monitor system health and metrics",
            Set.of(
                    AdminPermission.VIEW_SYSTEM_METRICS,
                    AdminPermission.VIEW_SYSTEM_HEALTH,
                    AdminPermission.VIEW_ACTIVITY_REPORTS,
                    AdminPermission.VIEW_AUDIT_LOGS
            ));

    private final String displayName;
    private final String description;
    private final Set<AdminPermission> defaultPermissions;

    AdminRole(String displayName, String description, Set<AdminPermission> defaultPermissions) {
        this.displayName = displayName;
        this.description = description;
        this.defaultPermissions = defaultPermissions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Set<AdminPermission> getDefaultPermissions() {
        return defaultPermissions;
    }

    public boolean hasPermission(AdminPermission permission) {
        return defaultPermissions.contains(permission);
    }
}