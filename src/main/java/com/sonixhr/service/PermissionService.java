package com.sonixhr.service;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {
    // =====================================================
    // Check if current authenticated user has a specific permission
    // =====================================================
    public boolean hasPermission(String permissionName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Object principal = auth.getPrincipal();

        // Check for Employee (tenant user)
        if (principal instanceof Employee) {
            Employee employee = (Employee) principal;
            boolean hasPermission = employee.hasPermission(permissionName);
            if (log.isDebugEnabled()) {
                log.debug("Employee {} permission check for '{}': {}",
                        employee.getEmail(), permissionName, hasPermission);
            }
            return hasPermission;
        }

        // Check for PlatformUser (platform admin)
        if (principal instanceof PlatformUser) {
            PlatformUser platformUser = (PlatformUser) principal;
            boolean hasPermission = platformUser.hasPermission(permissionName);
            if (log.isDebugEnabled()) {
                log.debug("Platform user {} permission check for '{}': {}",
                        platformUser.getEmail(), permissionName, hasPermission);
            }
            return hasPermission;
        }

        log.warn("Unknown principal type: {}", principal != null ? principal.getClass() : "null");
        return false;
    }


    // For use in @PreAuthorize with custom evaluator
    public boolean hasPermission(Authentication auth, String permissionName) {
        return hasPermission(permissionName);
    }


    // Check if current user has any of the specified permissions
    public boolean hasAnyPermission(String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (hasPermission(permissionName)) {
                return true;
            }
        }
        return false;
    }


    // Check if current user has all of the specified permissions
      public boolean hasAllPermissions(String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (!hasPermission(permissionName)) {
                return false;
            }
        }
        return true;
    }

    // =====================================================
    // Get current user type (EMPLOYEE, PLATFORM, etc.)
    // =====================================================
    public String getCurrentUserType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "ANONYMOUS";
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof Employee) {
            return "EMPLOYEE";
        }
        if (principal instanceof PlatformUser) {
            return "PLATFORM";
        }
        return "UNKNOWN";
    }

    // =====================================================
    // Get current employee if authenticated as employee
    // =====================================================
    public Employee getCurrentEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Employee) {
                return (Employee) principal;
            }
        }
        return null;
    }

    // =====================================================
    // Get current platform user if authenticated as platform user
    // =====================================================

    public PlatformUser getCurrentPlatformUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof PlatformUser) {
                return (PlatformUser) principal;
            }
        }
        return null;
    }

    // =====================================================
    // Check if current user is a tenant employee
    // =====================================================
    public boolean isEmployee() {
        return getCurrentEmployee() != null;
    }

    // =====================================================
    // Check if current user is a platform user
    // =====================================================
    public boolean isPlatformUser() {
        return getCurrentPlatformUser() != null;
    }

    // =====================================================
    // Check if current user is super admin (platform admin)
    // =====================================================
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof PlatformUser) {
            return ((PlatformUser) principal).isSuperAdmin();
        }

        if (principal instanceof Employee) {
            return ((Employee) principal).isSuperAdmin();
        }

        return false;
    }


    // Get current tenant ID if user is an employee
       public Long getCurrentTenantId() {
        Employee employee = getCurrentEmployee();
        return employee != null ? employee.getTenantId() : null;
    }


    // Get current user ID (employee ID or platform user ID)
      public Long getCurrentEmployeeId() {
        Employee employee = getCurrentEmployee();
        return employee != null ? employee.getId() : null;
    }
}