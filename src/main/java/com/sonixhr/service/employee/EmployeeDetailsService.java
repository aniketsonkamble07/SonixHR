package com.sonixhr.service.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Slf4j
@Service("employeeDetailsService")
@Primary
@RequiredArgsConstructor
public class EmployeeDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final TenantRoleRepository roleRepository;  // ✅ Added for default role

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Loading employee by email: {}", email);

        // ✅ REMOVED redundant existsByEmail query - single DB call
        Employee employee = employeeRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("Employee not found for email: {}", email);
                    return new UsernameNotFoundException("Employee not found: " + email);
                });

        log.info("Employee loaded - ID: {}, TenantId: {}, Active: {}, Roles found: {}",
                employee.getId(), employee.getTenantId(), employee.isActive(),
                employee.getRoles() != null ? employee.getRoles().size() : 0);

        // =====================================================
        // ✅ CRITICAL FIX: Validate and fix roles
        // =====================================================

        // Check if employee has any roles
        if (employee.getRoles() == null || employee.getRoles().isEmpty()) {
            log.error("Employee {} has NO roles assigned!", email);

            // ✅ Option 1: Throw exception (Recommended for production)
            throw new UsernameNotFoundException(
                    "Account not properly configured. Please contact administrator.");

            // ✅ Option 2: Assign default role (if you want auto-fix)
            // assignDefaultRoleToEmployee(employee);
        }

        // Log role details for debugging
        log.info("Roles found for employee {}: {}", email, employee.getRoles().size());
        for (TenantRole role : employee.getRoles()) {
            log.info("  - Role: {} (ID: {}), Permissions count: {}",
                    role.getName(), role.getId(),
                    role.getPermissions() != null ? role.getPermissions().size() : 0);

            // Check if role has permissions
            if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
                log.warn("    Role '{}' has NO permissions assigned!", role.getName());
            } else {
                role.getPermissions().forEach(perm ->
                        log.debug("      Permission: {}", perm.getPermission().name()));
            }
        }

        // ✅ Check authorities count
        var authorities = employee.getAuthorities();
        log.info("Authorities count for {}: {}", email, authorities.size());

        if (authorities.isEmpty()) {
            log.warn("Employee {} has NO authorities! This will cause Access Denied.", email);

            // Check if roles exist but have no permissions
            if (employee.getRoles() != null && !employee.getRoles().isEmpty()) {
                log.error("Roles exist but have no permissions assigned. Check role-permission mapping.");
            }
        }

        // Check account status
        if (!employee.isEnabled()) {
            log.warn("Account disabled for email: {}", email);
            throw new UsernameNotFoundException("Employee account is disabled");
        }

        if (!employee.isAccountNonLocked()) {
            log.warn("Account locked for email: {}", email);
            throw new UsernameNotFoundException("Employee account is locked");
        }

        log.info("Employee authenticated successfully: {} with {} authorities",
                email, authorities.size());
        return employee;
    }

    /**
     * ✅ Optional: Auto-assign default role to employees with no roles
     */
    @Transactional
    protected void assignDefaultRoleToEmployee(Employee employee) {
        try {
            TenantRole defaultRole = roleRepository.findDefaultRoleByTenantId(employee.getTenantId())
                    .orElseThrow(() -> new RuntimeException("No default role found for tenant"));

            if (employee.getRoles() == null) {
                employee.setRoles(new HashSet<>());
            }
            employee.getRoles().add(defaultRole);

            Employee saved = employeeRepository.save(employee);
            log.info("Assigned default role '{}' to employee: {}",
                    defaultRole.getName(), saved.getEmail());
        } catch (Exception e) {
            log.error("Failed to assign default role to employee: {}", employee.getEmail(), e);
            throw new UsernameNotFoundException("Account configuration error");
        }
    }
}