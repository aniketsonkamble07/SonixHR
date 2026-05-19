package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.*;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;


@RestController
@RequestMapping("/api/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_ADMIN')")
    public ResponseEntity<PlatformUserResponse> createUser(
            @Valid @RequestBody PlatformUserCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {
        PlatformUserResponse response = platformUserService.createUser(request, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/resend-activation")
    @PreAuthorize("hasAuthority('CREATE_ADMIN')")
    public ResponseEntity<Void> resendActivationEmail(@PathVariable UUID id) {
        PlatformUserResponse user = platformUserService.getUserById(id);
        platformUserService.resendActivationEmail(user.getEmail());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ADMINS')")
    public ResponseEntity<Page<PlatformUserResponse>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PlatformUserResponse> users = platformUserService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserById(@PathVariable UUID id) {
        PlatformUserResponse user = platformUserService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/by-email/{email}")
    @PreAuthorize("hasAuthority('VIEW_ADMINS')")
    public ResponseEntity<PlatformUserResponse> getUserByEmail(@PathVariable String email) {
        PlatformUserResponse user = platformUserService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EDIT_ADMIN')")
    public ResponseEntity<PlatformUserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformUserUpdateRequest request) {
        PlatformUserResponse user = platformUserService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('MANAGE_ADMIN_ROLES')")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable UUID id,
            @RequestBody Set<Long> roleIds) {
        platformUserService.updateUserRoles(id, roleIds);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EDIT_ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        platformUserService.deactivateUser(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('EDIT_ADMIN')")
    public ResponseEntity<Void> activateUserByAdmin(@PathVariable UUID id) {
        platformUserService.activateUserByAdmin(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        platformUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/change-password")
    @PreAuthorize("hasAuthority('RESET_ADMIN_PASSWORD')")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID id,
            @RequestBody ChangePasswordRequest request) {
        platformUserService.forceChangePassword(id, request.getNewPassword(), request.isMustChangePassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_METRICS')")
    public ResponseEntity<PlatformUserStatistics> getUserStatistics() {
        PlatformUserStatistics statistics = platformUserService.getUserStatistics();
        return ResponseEntity.ok(statistics);
    }
}