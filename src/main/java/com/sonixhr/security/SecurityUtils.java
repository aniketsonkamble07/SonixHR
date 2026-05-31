package com.sonixhr.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SecurityUtils {

    /**
     * Get current authentication
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Get current user's email
     */
    public String getCurrentUserEmail() {
        Authentication auth = getCurrentAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /**
     * Get current user's roles from JWT
     */
    @SuppressWarnings("unchecked")
    public List<String> getCurrentUserRoles() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptyList();
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object roles = detailsMap.get("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Check if current user has a specific role
     */
    public boolean hasRole(String role) {
        List<String> roles = getCurrentUserRoles();
        return roles.contains(role) || roles.contains("ROLE_" + role);
    }

    /**
     * Check if current user is SUPER_ADMIN
     */
    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    /**
     * Check if current user is HR
     */
    public boolean isHR() {
        return hasRole("HR") || hasRole("ADMIN") || hasRole("ROLE_HR");
    }

    /**
     * Get current user type (TENANT, PLATFORM, EMPLOYEE)
     */
    public String getCurrentUserType() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object userType = detailsMap.get("userType");
            if (userType instanceof String) {
                return (String) userType;
            }
        }
        return null;
    }

    /**
     * Get current tenant ID
     */
    public UUID getCurrentTenantId() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object tenantId = detailsMap.get("tenantId");
            if (tenantId instanceof String) {
                return UUID.fromString((String) tenantId);
            }
        }
        return null;
    }

    /**
     * Get current employee ID
     */
    public Long getCurrentEmployeeId() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object employeeId = detailsMap.get("employeeId");
            if (employeeId instanceof Integer) {
                return ((Integer) employeeId).longValue();
            }
            if (employeeId instanceof Long) {
                return (Long) employeeId;
            }
        }
        return null;
    }

    /**
     * Get current employee code
     */
    public String getCurrentEmployeeCode() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object employeeCode = detailsMap.get("employeeCode");
            if (employeeCode instanceof String) {
                return (String) employeeCode;
            }
        }
        return null;
    }
}
