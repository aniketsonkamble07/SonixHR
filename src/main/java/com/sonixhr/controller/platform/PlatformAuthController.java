package com.sonixhr.controller.platform;

import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.dto.ActivationRequest;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.PlatformTokenBlacklistService;
import com.sonixhr.service.platform.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    @Qualifier("platformAuthenticationManager")
    private final AuthenticationManager platformAuthenticationManager;
    private final JwtService jwtService;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformUserService platformUserService;
    private final PlatformPermissionRepository permissionRepository;
    private final PlatformRoleRepository roleRepository;
    private final PlatformTokenBlacklistService tokenBlacklistService;  // ✅ For logout

    @PostMapping("/login")
    @Transactional  // ✅ Added for database update
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        log.info("Login request for platform user: {}", request.getEmail());

        try {
            Authentication auth = platformAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            PlatformUser user = (PlatformUser) auth.getPrincipal();

            // Update last login details
            platformUserRepository.save(user);

            var tokenPair = jwtService.generatePlatformTokenPair(user);

            log.info("Platform user logged in successfully: {}", user.getEmail());

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType("Bearer")
                    .expiresIn(tokenPair.getExpiresIn())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build());
        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getEmail());
            throw new RuntimeException("Invalid email or password");
        } catch (Exception e) {
            log.error("Login error for user {}: {}", request.getEmail(), e.getMessage());
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal PlatformUser user,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Logout request for platform user: {}", user != null ? user.getEmail() : "unknown");

        // ✅ Blacklist the token if provided
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklistToken(token);
            log.info("Token blacklisted for user: {}", user != null ? user.getEmail() : "unknown");
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        log.info("Token refresh request");

        try {
            // ✅ Check if token is blacklisted
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                throw new RuntimeException("Token has been revoked");
            }

            if (jwtService.validateToken(refreshToken) && jwtService.isRefreshToken(refreshToken)) {
                String username = jwtService.extractUsername(refreshToken);
                PlatformUser user = platformUserRepository.findByEmail(username)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                var tokenPair = jwtService.generatePlatformTokenPair(user);

                return ResponseEntity.ok(LoginResponse.builder()
                        .accessToken(tokenPair.getAccessToken())
                        .refreshToken(tokenPair.getRefreshToken())
                        .tokenType("Bearer")
                        .expiresIn(tokenPair.getExpiresIn())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .build());
            }
            throw new RuntimeException("Invalid or expired refresh token");
        } catch (Exception e) {
            log.error("Refresh token error: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh token: " + e.getMessage());
        }
    }

    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, Object>> testAuth(Authentication authentication) {
        log.debug("Testing authentication - Authentication present: {}", authentication != null);

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "email", "null",
                    "authorities", "[]",
                    "message", "No authentication found"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("name", authentication.getName());
        response.put("authorities", authentication.getAuthorities().toString());

        Object principal = authentication.getPrincipal();
        if (principal instanceof PlatformUser) {
            PlatformUser user = (PlatformUser) principal;
            response.put("userType", "PLATFORM");
            response.put("email", user.getEmail());
            response.put("fullName", user.getFullName());
            response.put("designation", user.getDesignation());
        } else if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            response.put("userType", "PLATFORM_USERDETAILS");
            response.put("email", userDetails.getUsername());
        } else {
            response.put("userType", "UNKNOWN");
            response.put("principalClass", principal.getClass().getName());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        log.info("Getting current platform user info");

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "authenticated", false,
                    "message", "Not authenticated"
            ));
        }

        String email = authentication.getName();
        PlatformUser user = platformUserRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "email", email,
                    "message", "User authenticated but not found in database"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("userType", "PLATFORM");
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("designation", user.getDesignation());
        response.put("status", user.getStatus());
        response.put("isActive", user.getStatus().isActive());
        response.put("authorities", authentication.getAuthorities().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateUser(@Valid @RequestBody ActivationRequest request) {
        log.info("Activating platform user with token: {}", request.getToken());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        PlatformUser user = platformUserService.activateUser(
                request.getToken(),
                request.getPassword(),
                request.getConfirmPassword()
        );

        var tokenPair = jwtService.generatePlatformTokenPair(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account activated successfully!");
        response.put("accessToken", tokenPair.getAccessToken());
        response.put("refreshToken", tokenPair.getRefreshToken());
        response.put("tokenType", "Bearer");
        response.put("expiresIn", tokenPair.getExpiresIn());
        response.put("userType", "PLATFORM");
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("designation", user.getDesignation());

        log.info("Platform user activated successfully: {}", user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam String email) {
        log.info("Forgot password request for email: {}", email);

        platformUserService.forgotPassword(email);

        return ResponseEntity.ok(Map.of(
                "message", "If the email exists, a password reset link has been sent.",
                "status", "success"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword) {

        log.info("Reset password request with token: {}", token);

        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match");
        }

        platformUserService.resetPasswordWithToken(token, newPassword, confirmPassword);

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully!",
                "status", "success"
        ));
    }

    @GetMapping("/verify-token")
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestParam String token) {
        log.debug("Verifying token");

        try {
            // ✅ Check if token is blacklisted
            if (tokenBlacklistService.isBlacklisted(token)) {
                return ResponseEntity.ok(Map.of(
                        "valid", false,
                        "error", "Token has been revoked"
                ));
            }

            boolean isValid = jwtService.validateToken(token);
            String userType = null;
            String email = null;

            if (isValid) {
                userType = jwtService.extractUserType(token);
                email = jwtService.extractUsername(token);
            }

            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "userType", userType != null ? userType : "unknown",
                    "email", email != null ? email : "unknown"
            ));
        } catch (Exception e) {
            log.error("Token verification error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/debug/security-context")
    public ResponseEntity<Map<String, Object>> debugSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new HashMap<>();
        result.put("authentication", auth != null);
        result.put("authenticated", auth != null && auth.isAuthenticated());

        if (auth != null) {
            result.put("name", auth.getName());
            result.put("principalClass", auth.getPrincipal().getClass().getName());
            result.put("authorities", auth.getAuthorities().toString());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/debug/platform-setup")
    public ResponseEntity<Map<String, Object>> checkPlatformSetup() {
        Map<String, Object> info = new HashMap<>();

        // Check permissions
        long permissionCount = permissionRepository.count();
        info.put("totalPermissions", permissionCount);

        // Check roles
        List<PlatformRole> roles = roleRepository.findAll();
        info.put("totalRoles", roles.size());
        info.put("roles", roles.stream()
                .map(r -> Map.of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "permissionCount", r.getPermissions() != null ? r.getPermissions().size() : 0
                ))
                .collect(Collectors.toList()));

        // ✅ FIXED: Use correct Super Admin email
        var superAdminOpt = platformUserRepository.findByEmail("admin@sonixhr.com");
        if (superAdminOpt.isPresent()) {
            PlatformUser superAdmin = superAdminOpt.get();
            info.put("superAdminExists", true);
            info.put("superAdminId", superAdmin.getId());
            info.put("superAdminStatus", superAdmin.getStatus());
            info.put("superAdminRoles", superAdmin.getRoles().size());
            info.put("superAdminAuthorities", superAdmin.getAuthorities().size());
        } else {
            info.put("superAdminExists", false);
            info.put("expectedSuperAdminEmail", "admin@sonixhr.com");
        }

        return ResponseEntity.ok(info);
    }
}