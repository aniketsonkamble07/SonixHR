package com.sonixhr.controller.platform;

import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Only loaded in the "dev" profile. Will never be registered as a bean in prod.
// Run with: -Dspring.profiles.active=dev
// Or set spring.profiles.active=dev in application-dev.yml.

@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/api/platform/debug")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformDebugController {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;

    @GetMapping("/security-context")
    public ResponseEntity<Map<String, Object>> debugSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new HashMap<>();
        result.put("authenticationPresent", auth != null);
        result.put("authenticated", auth != null && auth.isAuthenticated());

        if (auth != null) {
            result.put("name", auth.getName());
            result.put("principalClass", auth.getPrincipal().getClass().getName());
            result.put("authorities", auth.getAuthorities().toString());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(
            @AuthenticationPrincipal Object principal,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "message", "No authentication found"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("name", authentication.getName());
        response.put("authorities", authentication.getAuthorities().toString());

        if (principal instanceof PlatformUser user) {
            response.put("userType", "PLATFORM");
            response.put("email", user.getEmail());
            response.put("fullName", user.getFullName());
        } else if (principal instanceof UserDetails ud) {
            response.put("userType", "USERDETAILS");
            response.put("email", ud.getUsername());
        } else {
            response.put("userType", "UNKNOWN");
            response.put("principalClass", principal.getClass().getName());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/platform-setup")
    public ResponseEntity<Map<String, Object>> checkPlatformSetup() {
        Map<String, Object> info = new HashMap<>();

        info.put("totalPermissions", permissionRepository.count());

        List<PlatformRole> roles = roleRepository.findAll();
        info.put("totalRoles", roles.size());
        info.put("roles", roles.stream()
                .map(r -> Map.of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "permissionCount", r.getPermissions() != null ? r.getPermissions().size() : 0
                ))
                .collect(Collectors.toList()));

        platformUserRepository.findByEmail("admin@sonixhr.com").ifPresentOrElse(
                superAdmin -> {
                    info.put("superAdminExists", true);
                    info.put("superAdminId", superAdmin.getId());
                    info.put("superAdminStatus", superAdmin.getStatus());
                    info.put("superAdminRoles", superAdmin.getRoles().size());
                    info.put("superAdminAuthorities", superAdmin.getAuthorities().size());
                },
                () -> {
                    info.put("superAdminExists", false);
                    info.put("expectedEmail", "admin@sonixhr.com");
                }
        );

        return ResponseEntity.ok(info);
    }
}