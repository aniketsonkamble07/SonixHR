package com.sonixhr.service.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service("employeeDetailsService")
@Primary
@RequiredArgsConstructor
public class EmployeeDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("========== STEP 1: Loading employee by email: {} ==========", email);

        // Step 1: Check if employee exists in database
        boolean exists = employeeRepository.existsByEmail(email);
        log.info("STEP 1 - Employee exists in DB: {}", exists);

        if (!exists) {
            log.warn("STEP 1 - FAILED: Employee not found for email: {}", email);
            throw new UsernameNotFoundException("Employee not found: " + email);
        }

        // Step 2: Load employee with roles and permissions
        log.info("STEP 2: Loading employee with roles and permissions...");
        Employee employee = employeeRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("STEP 2 - FAILED: Employee not found with join fetch for email: {}", email);
                    return new UsernameNotFoundException("Employee not found: " + email);
                });
        log.info("STEP 2 - SUCCESS: Employee loaded from DB");

        // Step 3: Check employee basic info
        log.info("STEP 3: Employee basic info - ID: {}, TenantId: {}, Active: {}",
                employee.getId(), employee.getTenantId(), employee.isActive());

        // Step 4: Check roles
        log.info("STEP 4: Checking roles...");
        log.info("STEP 4 - Roles collection is null? {}", employee.getRoles() == null);
        if (employee.getRoles() != null) {
            log.info("STEP 4 - Roles size: {}", employee.getRoles().size());
            if (employee.getRoles().isEmpty()) {
                log.warn("STEP 4 - WARNING: Employee has NO roles assigned!");
            } else {
                employee.getRoles().forEach(role -> {
                    log.info("STEP 4 - Role ID: {}, Name: {}", role.getId(), role.getName());
                    log.info("STEP 4 - Role permissions size: {}", role.getPermissions() != null ? role.getPermissions().size() : 0);
                    if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                        role.getPermissions().forEach(perm ->
                                log.info("STEP 4 - Permission: {}", perm.getPermission().name()));
                    }
                });
            }
        } else {
            log.warn("STEP 4 - WARNING: Roles collection is NULL!");
        }

        // Step 5: Check authorities
        log.info("STEP 5: Getting authorities...");
        var authorities = employee.getAuthorities();
        log.info("STEP 5 - Authorities size: {}", authorities.size());
        if (authorities.isEmpty()) {
            log.warn("STEP 5 - WARNING: No authorities found!");
        } else {
            authorities.forEach(auth -> log.info("STEP 5 - Authority: {}", auth.getAuthority()));
        }

        // Step 6: Check account status
        log.info("STEP 6: Account status - isActive: {}, isEnabled: {}, isAccountNonLocked: {}",
                employee.isActive(), employee.isEnabled(), employee.isAccountNonLocked());

        if (!employee.isActive()) {
            log.warn("STEP 6 - FAILED: Employee account is inactive for email: {}", email);
            throw new UsernameNotFoundException("Employee account is inactive");
        }

        log.info("========== FINAL: Employee loaded successfully: {} ==========", email);
        log.info("Final summary - Roles: {}, Authorities: {}",
                employee.getRoles().size(), authorities.size());

        return employee;
    }
}