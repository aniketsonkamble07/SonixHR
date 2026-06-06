package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformUserCreateRequest;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformUserStatistics;
import com.sonixhr.dto.platform.PlatformUserUpdateRequest;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformUserStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.DuplicateResourceException;
import com.sonixhr.exceptions.NotFoundException;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformRoleRepository platformRoleRepository;
    private final ActivationTokenService activationTokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    // =====================================================
    // CREATE & ACTIVATION METHODS
    // =====================================================

    @Transactional
    public PlatformUserResponse createUser(PlatformUserCreateRequest request, Long createdBy) {
        log.info("Creating platform user: {}", request.getEmail());

        // Check for existing user
        if (platformUserRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        // Validate roles exist
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(request.getRoleIds()));
        if (roles.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }

        // Verify all requested roles were found
        if (roles.size() != request.getRoleIds().size()) {
            Set<Long> foundRoleIds = roles.stream().map(PlatformRole::getId).collect(Collectors.toSet());
            Set<Long> missingRoleIds = request.getRoleIds().stream()
                    .filter(id -> !foundRoleIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid role IDs: " + missingRoleIds);
        }

        // ✅ FIX: Set tenantId - default to system tenant (1) if not provided
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            tenantId = 1L; // System tenant
            log.warn("tenantId was null for user {}, defaulting to system tenant (1)", request.getEmail());
        }

        // Create user with temporary disabled password
        PlatformUser user = PlatformUser.builder()
                .email(request.getEmail())
                .password("TEMPORARY_DISABLED")
                .fullName(request.getFullName())
                .designation(request.getDesignation())
                .tenantId(tenantId)  // ✅ ADD THIS LINE
                .status(PlatformUserStatus.PENDING_VERIFICATION)
                .isActive(false)
                .isEnabled(false)
                .isAccountNonLocked(true)
                .isAccountNonExpired(true)
                .isCredentialsNonExpired(true)
                .roles(roles)
                .createdBy(createdBy)
                .build();

        PlatformUser savedUser = platformUserRepository.save(user);

        // Generate activation token and send email
        String activationTokenValue = activationTokenService.generateTokenForPlatformUser(savedUser.getId());
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + activationTokenValue;

        emailService.sendPlatformActivationEmail(savedUser.getEmail(), savedUser.getFullName(), activationLink);

        log.info("Platform user created with pending verification: {}", request.getEmail());

        return toResponse(savedUser, activationLink, LocalDateTime.now().plusHours(24));
    }

    @Transactional
    public PlatformUser activateUser(String token, String password, String confirmPassword) {
        log.info("Activating user with token");

        if (!password.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        validatePasswordStrength(password);

        PlatformUser activatedUser = activationTokenService.activatePlatformUser(token, password);

        log.info("User activated successfully: {}", activatedUser.getEmail());

        // Send welcome email after successful activation
        emailService.sendWelcomeEmail(activatedUser.getEmail(), activatedUser.getFullName());

        return activatedUser;
    }

    @Transactional
    public void resendActivationEmail(String email) {
        log.info("Resending activation email to: {}", email);

        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        if (user.isActive()) {
            throw new BusinessException("User is already activated");
        }

        if (user.getStatus() != PlatformUserStatus.PENDING_VERIFICATION) {
            throw new BusinessException("User is not in pending verification status");
        }

        String newToken = activationTokenService.generateTokenForPlatformUser(user.getId());
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + newToken;
        emailService.sendPlatformActivationEmail(user.getEmail(), user.getFullName(), activationLink);

        log.info("Activation email resent to: {}", email);
    }

    // =====================================================
    // GET METHODS
    // =====================================================

    public PlatformUserResponse getUserById(Long userId) {
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return toResponse(user);
    }

    public PlatformUserResponse getUserByEmail(String email) {
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        return toResponse(user);
    }

    public Page<PlatformUserResponse> getAllUsers(Pageable pageable) {
        return platformUserRepository.findAll(pageable).map(this::toResponse);
    }

    // =====================================================
    // UPDATE METHODS
    // =====================================================

    @Transactional
    public PlatformUserResponse updateUser(Long userId, PlatformUserUpdateRequest request) {
        log.info("Updating platform user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        // Prevent modification of system-protected users
        if (user.isSystemProtected()) {
            throw new BusinessException("Cannot modify system-protected user");
        }

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName());
        }

        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (platformUserRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        user.setUpdatedBy(getCurrentUserId());
        PlatformUser updated = platformUserRepository.save(user);

        log.info("Platform user updated: {}", userId);
        return toResponse(updated);
    }

    @Transactional
    public PlatformUserResponse updateUserStatus(Long userId, PlatformUserStatus status) {
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        // Prevent status change for system-protected users
        if (user.isSystemProtected()) {
            throw new BusinessException("Cannot modify status of system-protected user");
        }

        user.setStatus(status);
        user.setActive(status == PlatformUserStatus.ACTIVE);
        user.setEnabled(status == PlatformUserStatus.ACTIVE);
        user.setUpdatedBy(getCurrentUserId());

        PlatformUser updated = platformUserRepository.save(user);
        log.info("User {} status updated to: {}", userId, status);

        return toResponse(updated);
    }

    @Transactional
    public PlatformUserResponse updateUserRoles(Long userId, Set<Long> roleIds) {
        log.info("Updating roles for user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        // Validate roles exist
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(roleIds));
        if (roles.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }

        if (roles.size() != roleIds.size()) {
            Set<Long> foundRoleIds = roles.stream().map(PlatformRole::getId).collect(Collectors.toSet());
            Set<Long> missingRoleIds = roleIds.stream()
                    .filter(id -> !foundRoleIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid role IDs: " + missingRoleIds);
        }

        user.setRoles(roles);
        user.setUpdatedBy(getCurrentUserId());

        PlatformUser updated = platformUserRepository.save(user);
        log.info("Roles updated for user: {}", userId);

        return toResponse(updated);
    }

    // =====================================================
    // ACTIVATION/DEACTIVATION METHODS
    // =====================================================

    @Transactional
    public void activateUserByAdmin(Long userId) {
        log.info("Activating platform user by admin: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.isSystemProtected()) {
            throw new BusinessException("Cannot activate/deactivate system-protected user");
        }

        user.setActive(true);
        user.setEnabled(true);
        user.setStatus(PlatformUserStatus.ACTIVE);
        user.setUpdatedBy(getCurrentUserId());
        user.setLockTime(null);
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);

        platformUserRepository.save(user);
        log.info("Platform user activated by admin: {}", userId);

        // Send notification email
        emailService.sendAccountActivatedNotification(user.getEmail(), user.getFullName());
    }

    @Transactional
    public void deactivateUser(Long userId) {
        log.info("Deactivating platform user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.isSystemProtected()) {
            throw new BusinessException("Cannot deactivate system-protected user");
        }

        user.setActive(false);
        user.setEnabled(false);
        user.setStatus(PlatformUserStatus.INACTIVE);
        user.setUpdatedBy(getCurrentUserId());

        platformUserRepository.save(user);
        log.info("Platform user deactivated: {}", userId);

        // Send notification email
        emailService.sendAccountDeactivatedNotification(user.getEmail(), user.getFullName());
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("Soft deleting platform user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.isSystemProtected()) {
            throw new BusinessException("Cannot delete system-protected user");
        }

        user.setActive(false);
        user.setEnabled(false);
        user.setStatus(PlatformUserStatus.DELETED);
        user.setUpdatedBy(getCurrentUserId());
        user.setDeletedAt(LocalDateTime.now());

        platformUserRepository.save(user);
        log.info("User {} soft deleted", userId);
    }

    // =====================================================
    // PASSWORD METHODS
    // =====================================================

    @Transactional
    public void forgotPassword(String email) {
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        if (!user.isActive() || user.getStatus() != PlatformUserStatus.ACTIVE) {
            throw new BusinessException("Cannot reset password for inactive user");
        }

        String resetToken = activationTokenService.generatePasswordResetTokenForPlatformUser(user.getId());
        String resetLink = baseUrl + "/api/platform/auth/reset-password?token=" + resetToken;

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
        log.info("Password reset email sent to: {}", email);
    }

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword, String confirmPassword) {
        log.info("Resetting password with token");

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);
        activationTokenService.resetPasswordForPlatformUser(token, newPassword);

        log.info("Password reset successfully with token");
    }

    @Transactional
    public void resetPasswordByAdmin(Long userId, String newPassword, boolean forceChange) {
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        validatePasswordStrength(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);  // Fixed: Changed from setPasswordHash to setPassword
        user.setPasswordLastChanged(LocalDateTime.now());
        user.setMustChangePassword(forceChange);
        user.setUpdatedBy(getCurrentUserId());
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);

        platformUserRepository.save(user);
        log.info("Password reset by admin for user: {}", userId);

        emailService.sendPasswordResetNotification(user.getEmail(), user.getFullName());
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("New passwords do not match");
        }

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        validatePasswordStrength(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setPasswordLastChanged(LocalDateTime.now());
        user.setMustChangePassword(false);
        user.setUpdatedBy(userId);

        platformUserRepository.save(user);
        log.info("Password changed successfully for user: {}", userId);
    }

    // =====================================================
    // STATISTICS METHODS
    // =====================================================

    public PlatformUserStatistics getUserStatistics() {
        return PlatformUserStatistics.builder()
                .totalUsers(platformUserRepository.count())
                .activeUsers(platformUserRepository.countByIsActiveTrue())
                .inactiveUsers(platformUserRepository.countByIsActiveFalse())
                .pendingVerification(platformUserRepository.countByStatus(PlatformUserStatus.PENDING_VERIFICATION))
                .suspendedUsers(platformUserRepository.countByStatus(PlatformUserStatus.SUSPENDED))
                .lockedUsers(platformUserRepository.countByStatus(PlatformUserStatus.LOCKED))
                .build();
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Password must contain at least one number");
        }
        if (!password.matches(".*[@#$%^&+=!].*")) {
            throw new BusinessException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof PlatformUser) {
            return ((PlatformUser) authentication.getPrincipal()).getId();
        }
        return null;
    }

    private PlatformUserResponse toResponse(PlatformUser user) {
        if (user == null) {
            return null;
        }

        return PlatformUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .status(user.getStatus())
                .isActive(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles().stream()
                        .map(role -> PlatformUserResponse.PlatformRoleResponse.builder()
                                .id(role.getId())
                                .name(role.getName())
                                .description(role.getDescription())
                                .build())
                        .collect(Collectors.toSet()))
                .build();
    }

    private PlatformUserResponse toResponse(PlatformUser user, String invitationLink, LocalDateTime expiryTime) {
        PlatformUserResponse response = toResponse(user);
        if (response != null) {
            response.setInvitationLink(invitationLink);
            response.setInvitationExpiryAt(expiryTime);
        }
        return response;
    }
}