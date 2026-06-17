package com.sonixhr.controller;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final UserDetailsService platformUserDetailsService;
    private final UserDetailsService employeeDetailsService;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DebugController(
            @Qualifier("platformUserDetailsService") UserDetailsService platformUserDetailsService,
            @Qualifier("employeeDetailsService") UserDetailsService employeeDetailsService,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder) {
        this.platformUserDetailsService = platformUserDetailsService;
        this.employeeDetailsService = employeeDetailsService;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Debug endpoint for Employee (tenant user)
     */
    @GetMapping("/employee-auth")
    public ResponseEntity<Map<String, Object>> checkEmployeeAuth(@AuthenticationPrincipal Employee currentEmployee) {
        if (currentEmployee == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "authenticated", false,
                    "message", "No employee found in security context",
                    "userType", "EMPLOYEE"
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("userType", "EMPLOYEE");
        result.put("email", currentEmployee.getEmail());
        result.put("tenantId", currentEmployee.getTenantId());
        result.put("employeeId", currentEmployee.getId());
        result.put("employeeCode", currentEmployee.getEmployeeCode());
        result.put("fullName", currentEmployee.getFullName());
        result.put("firstName", currentEmployee.getFirstName());
        result.put("lastName", currentEmployee.getLastName());
        result.put("position", currentEmployee.getPosition());
        result.put("department", currentEmployee.getDepartment() != null ?
                currentEmployee.getDepartment().getName() : null);
        result.put("status", currentEmployee.getStatus() != null ?
                currentEmployee.getStatus().name() : null);
        result.put("isActive", currentEmployee.isActive());
        result.put("authorities", currentEmployee.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Debug endpoint for Platform User (admin)
     */
    @GetMapping("/platform-auth")
    public ResponseEntity<Map<String, Object>> checkPlatformAuth(@AuthenticationPrincipal PlatformUser currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "authenticated", false,
                    "message", "No platform user found in security context",
                    "userType", "PLATFORM"
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("userType", "PLATFORM");
        result.put("email", currentUser.getEmail());
        result.put("fullName", currentUser.getFullName());
        result.put("designation", currentUser.getDesignation());
        result.put("status", currentUser.getStatus());
        result.put("isActive", currentUser.isActive());
        result.put("authorities", currentUser.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Legacy endpoint (kept for backward compatibility)
     */
    @GetMapping("/auth-check")
    public ResponseEntity<Map<String, Object>> checkAuth(@AuthenticationPrincipal Object principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "authenticated", false,
                    "message", "No user found in security context"
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);

        if (principal instanceof Employee) {
            Employee emp = (Employee) principal;
            result.put("userType", "EMPLOYEE");
            result.put("email", emp.getEmail());
            result.put("tenantId", emp.getTenantId());
            result.put("employeeId", emp.getId());
            result.put("fullName", emp.getFullName());
        } else if (principal instanceof PlatformUser) {
            PlatformUser admin = (PlatformUser) principal;
            result.put("userType", "PLATFORM");
            result.put("email", admin.getEmail());
            result.put("fullName", admin.getFullName());
            result.put("designation", admin.getDesignation());
        } else {
            result.put("userType", "UNKNOWN");
            result.put("principalType", principal.getClass().getName());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-auth-flow")
    public ResponseEntity<?> testAuthFlow(@RequestParam String email, @RequestParam(required = false) String password) {
        Map<String, Object> result = new HashMap<>();

        log.info("Testing auth flow for email: {}", email);

        // Test platform user loading
        try {
            UserDetails platformUser = platformUserDetailsService.loadUserByUsername(email);
            result.put("platformUserExists", true);
            result.put("platformUsername", platformUser.getUsername());
            result.put("platformAuthoritiesCount", platformUser.getAuthorities().size());
            log.info("Platform user found: {}", platformUser.getUsername());
        } catch (Exception e) {
            result.put("platformUserExists", false);
            result.put("platformUserError", e.getMessage());
            log.error("Platform user not found: {}", e.getMessage());
        }

        // Test employee loading (should fail for platform user)
        try {
            employeeDetailsService.loadUserByUsername(email);
            result.put("employeeExists", true);
            log.info("Employee found (unexpected for platform user)");
        } catch (Exception e) {
            result.put("employeeExists", false);
            result.put("employeeError", e.getMessage());
            log.debug("Employee not found (expected for platform user)");
        }

        // Test password if provided
        if (password != null) {
            var userOpt = platformUserRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                // ✅ FIXED: Use getPassword() not getPasswordHash()
                boolean matches = passwordEncoder.matches(password, userOpt.get().getPassword());
                result.put("passwordMatches", matches);
                result.put("userActive", userOpt.get().isActive());
                result.put("userStatus", userOpt.get().getStatus().toString());
                log.info("Password matches: {}, User active: {}", matches, userOpt.get().isActive());
            } else {
                result.put("userFound", false);
                log.warn("User not found in database: {}", email);
            }
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/check-database")
    public ResponseEntity<?> checkDatabase(@RequestParam String email) {
        log.info("Checking database for user: {}", email);

        var userOpt = platformUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "exists", false,
                    "message", "User not found in database"
            ));
        }

        PlatformUser user = userOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("exists", true);
        result.put("email", user.getEmail());
        result.put("fullName", user.getFullName());
        result.put("isActive", user.isActive());
        result.put("status", user.getStatus().toString());
        // ✅ FIXED: Use getPassword() not getPasswordHash()
        String password = user.getPassword();
        result.put("passwordLength", password != null ? password.length() : 0);
        if (password != null && password.length() > 0) {
            result.put("hashPrefix", password.substring(0, Math.min(30, password.length())));
        }
        result.put("hasRoles", user.getRoles() != null && !user.getRoles().isEmpty());
        result.put("roleCount", user.getRoles() != null ? user.getRoles().size() : 0);

        log.info("Database check result: active={}, status={}", user.isActive(), user.getStatus());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-password")
    public ResponseEntity<?> testPassword(@RequestParam String email, @RequestParam String password) {
        log.info("Testing password for user: {}", email);

        var userOpt = platformUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "exists", false,
                    "message", "User not found"
            ));
        }

        PlatformUser user = userOpt.get();
        // ✅ FIXED: Use getPassword() not getPasswordHash()
        boolean matches = passwordEncoder.matches(password, user.getPassword());

        Map<String, Object> result = new HashMap<>();
        result.put("exists", true);
        result.put("passwordMatches", matches);
        result.put("isActive", user.isActive());
        result.put("status", user.getStatus().toString());
        result.put("userType", "PLATFORM");

        log.info("Password test result: matches={}, active={}", matches, user.isActive());

        return ResponseEntity.ok(result);
    }

    /**
     * Debug endpoint to check all platform users
     */
    @GetMapping("/all-users")
    public ResponseEntity<?> getAllPlatformUsers() {
        log.info("Getting all platform users");

        var users = platformUserRepository.findAll();
        var result = users.stream()
                .map(user -> Map.of(
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "isActive", user.isActive(),
                        "status", user.getStatus().toString(),
                        "roles", user.getRoles().stream().map(r -> r.getName()).toList()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "totalUsers", users.size(),
                "users", result
        ));
    }
}