package com.sonixhr.controller.platform;

import com.sonixhr.dto.ResetPasswordRequest;
import com.sonixhr.dto.platform.*;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    /**
     * Create a new platform user (Super Admin only)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_PLATFORM_ADMIN')")
    public ResponseEntity<PlatformUserResponse> createUser(
            @Valid @RequestBody PlatformUserCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {
        log.info("REST request to create platform user: {}", request.getEmail());
        PlatformUserResponse response = platformUserService.createUser(request, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Resend activation email to user
     */
    @PostMapping("/{id}/resend-activation")
    @PreAuthorize("hasAuthority('CREATE_PLATFORM_ADMIN')")
    public ResponseEntity<Void> resendActivationEmail(@PathVariable Long id) {
        log.info("REST request to resend activation email for user: {}", id);
        PlatformUserResponse user = platformUserService.getUserById(id);
        platformUserService.resendActivationEmail(user.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * Get all platform users with pagination
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<Page<PlatformUserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.debug("REST request to get all platform users");
        Page<PlatformUserResponse> users = platformUserService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }

    /**
     * Get platform user by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserById(@PathVariable Long id) {
        PlatformUserResponse user = platformUserService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    /**
     * Get platform user by email
     */
    @GetMapping("/by-email/{email}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserByEmail(@PathVariable String email) {
        log.debug("REST request to get platform user by email: {}", email);
        PlatformUserResponse user = platformUserService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    /**
     * Update platform user details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<PlatformUserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody PlatformUserUpdateRequest request) {
        log.info("REST request to update platform user: {}", id);
        PlatformUserResponse user = platformUserService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }

    /**
     * Update platform user roles
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ADMIN_ROLES')")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable Long id,
            @RequestBody Set<Long> roleIds) {
        log.info("REST request to update roles for user: {}", id);
        platformUserService.updateUserRoles(id, roleIds);
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivate platform user
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        log.info("REST request to deactivate platform user: {}", id);
        platformUserService.deactivateUser(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Activate platform user (by admin)
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<Void> activateUserByAdmin(@PathVariable Long id) {
        log.info("REST request to activate platform user by admin: {}", id);
        platformUserService.activateUserByAdmin(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Soft delete platform user
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("REST request to delete platform user: {}", id);
        platformUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset password for user (Admin)
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('RESET_PLATFORM_ADMIN_PASSWORD')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("REST request to reset password for user: {}", id);
        platformUserService.resetPasswordByAdmin(id, request.getNewPassword(),true);
        return ResponseEntity.ok().build();
    }

    /**
     * Get platform user statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS')")
    public ResponseEntity<PlatformUserStatistics> getUserStatistics() {
        log.debug("REST request to get platform user statistics");
        PlatformUserStatistics statistics = platformUserService.getUserStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get current logged-in user info
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlatformUserResponse> getCurrentUser(
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.debug("REST request to get current platform user");
        PlatformUserResponse user = platformUserService.getUserById(currentUser.getId());
        return ResponseEntity.ok(user);
    }
}