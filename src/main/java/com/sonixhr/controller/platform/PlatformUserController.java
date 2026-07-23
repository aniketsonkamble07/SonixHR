package com.sonixhr.controller.platform;

import com.sonixhr.dto.ResetPasswordRequest;
import com.sonixhr.dto.platform.*;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformUserService;
import com.sonixhr.service.platform.PlatformUserService.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@SuppressWarnings("null")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    // =====================================================
    // CREATE USER
    // =====================================================

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_PLATFORM_ADMIN')")
    public ResponseEntity<PlatformUserResponse> createUser(
            @Valid @RequestBody PlatformUserCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {
        log.info("REST request to create platform user: {}", request.getEmail());
        PlatformUserResponse response = platformUserService.createUser(request, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =====================================================
    // RESEND ACTIVATION
    // =====================================================

    @PostMapping("/{id}/resend-activation")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<Void> resendActivationEmail(@PathVariable Long id) {
        log.info("REST request to resend activation email for user: {}", id);
        PlatformUserResponse user = platformUserService.getUserById(id);
        platformUserService.resendActivationEmail(user.getEmail());
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // GET ALL USERS
    // =====================================================

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<PageResult<PlatformUserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.debug("REST request to get all platform users");
        return ResponseEntity.ok(platformUserService.getAllUsers(pageable));
    }

    // =====================================================
    // GET USER BY ID
    // =====================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserById(@PathVariable Long id) {
        log.debug("REST request to get platform user: {}", id);
        return ResponseEntity.ok(platformUserService.getUserById(id));
    }

    // =====================================================
    // GET USER BY EMAIL
    // =====================================================

    @GetMapping("/by-email/{email}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserByEmail(@PathVariable String email) {
        log.debug("REST request to get platform user by email: {}", email);
        return ResponseEntity.ok(platformUserService.getUserByEmail(email));
    }

    // =====================================================
    // UPDATE USER
    // =====================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<PlatformUserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody PlatformUserUpdateRequest request) {
        log.info("REST request to update platform user: {}", id);
        return ResponseEntity.ok(platformUserService.updateUser(id, request));
    }

    // =====================================================
    // UPDATE USER STATUS
    // =====================================================

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<PlatformUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        log.info("REST request to update status for user: {} to {}", id, request.getStatus());
        PlatformUserResponse response = platformUserService.updateUserStatus(id, request.getStatus());
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPDATE USER ROLES
    // =====================================================

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ADMIN_ROLES')")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable Long id,
            @RequestBody @Valid Set<Long> roleIds) {
        log.info("REST request to update roles for user: {}", id);
        if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("At least one role ID is required");
        }
        platformUserService.updateUserRoles(id, roleIds);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // ACTIVATE USER (Admin)
    // =====================================================

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<Void> activateUserByAdmin(@PathVariable Long id) {
        log.info("REST request to activate platform user by admin: {}", id);
        platformUserService.activateUserByAdmin(id);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // SUSPEND USER
    // =====================================================

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ADMIN')")
    public ResponseEntity<Void> suspendUser(@PathVariable Long id) {
        log.info("REST request to suspend platform user: {}", id);
        platformUserService.suspendUser(id);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // DELETE USER
    // =====================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("REST request to delete platform user: {}", id);
        platformUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // RESET PASSWORD (Admin)
    // =====================================================

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('RESET_PLATFORM_ADMIN_PASSWORD')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("REST request to reset password for user: {}", id);
        platformUserService.resetPasswordByAdmin(id, request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // GET STATISTICS
    // =====================================================

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS')")
    public ResponseEntity<PlatformUserStatistics> getUserStatistics() {
        log.debug("REST request to get platform user statistics");
        return ResponseEntity.ok(platformUserService.getUserStatistics());
    }

    // =====================================================
    // GET CURRENT USER
    // =====================================================

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlatformUserResponse> getCurrentUser(
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.debug("REST request to get current platform user");
        return ResponseEntity.ok(platformUserService.getUserById(currentUser.getId()));
    }

    // =====================================================
    // CLEAR CACHES (Admin Only)
    // =====================================================

    @DeleteMapping("/cache")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ADMIN_ROLES')")
    public ResponseEntity<Void> clearCaches() {
        log.info("REST request to clear all platform user caches");
        platformUserService.clearAllCaches();
        return ResponseEntity.noContent().build();
    }
}