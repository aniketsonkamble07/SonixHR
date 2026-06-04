package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformUserCreateRequest;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformUserStatistics;
import com.sonixhr.dto.platform.PlatformUserUpdateRequest;
import com.sonixhr.entity.ActivationToken;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformUserStatus;
import com.sonixhr.enums.UserType;
import com.sonixhr.exceptions.DuplicateResourceException;
import com.sonixhr.exceptions.NotFoundException;
import com.sonixhr.repository.ActivationTokenRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformRoleRepository platformRoleRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    /**
     * Create a new platform user with invitation
     */
    @Transactional
    public PlatformUserResponse createUser(PlatformUserCreateRequest request, UUID createdBy)    {
        log.info("Creating platform user: {}", request.getEmail());

        // Check if email already exists
        if (platformUserRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        // Get and validate roles
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(request.getRoleIds()));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be assigned");
        }

        // Create user with temporary disabled password
        PlatformUser user = PlatformUser.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode("TEMPORARY_DISABLED"))
                .fullName(request.getFullName())
                .designation(request.getDesignation())
                .status(PlatformUserStatus.PENDING_VERIFICATION)
                .active(false)
                .roles(roles)
                .createdBy(createdBy)
                .build();

        PlatformUser savedUser = platformUserRepository.save(user);
        log.info("Platform user created with ID: {}", savedUser.getId());

        // Generate activation token
        String activationTokenValue = UUID.randomUUID().toString();

        // Save activation token
        ActivationToken token = ActivationToken.builder()
                .token(activationTokenValue)
                .userId(savedUser.getId())
                .userType(UserType.PLATFORM)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        activationTokenRepository.save(token);

        // Build activation link
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + activationTokenValue;

        // Send activation email
     /*   emailService.sendPlatformActivationEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                activationLink
       );*/



        log.info("Platform user created and activation email sent to: {}", request.getEmail());

        return toResponse(savedUser, activationLink, token.getExpiryTime());
    }

    /**
     * Activate user account using token
     */
    @Transactional
    public PlatformUser activateUser(String token, String password) {
        log.info("Activating user with token: {}", token);

        // Validate token
        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiryTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token"));

        // Mark token as used
        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        // Get user
        PlatformUser user = platformUserRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate password strength
        validatePasswordStrength(password);

        // Update user
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setStatus(PlatformUserStatus.ACTIVE);
        user.setMustChangePassword(false);

        PlatformUser activatedUser = platformUserRepository.save(user);
        log.info("User activated successfully: {}", user.getEmail());

        // Send welcome email
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());

        return activatedUser;
    }

    /**
     * Resend activation email
     */
    @Transactional
    public void resendActivationEmail(String email) {
        log.info("Resending activation email to: {}", email);

        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));

        if (user.isActive()) {
            throw new RuntimeException("User is already activated");
        }

        // Delete old tokens
        activationTokenRepository.deleteByUserId(user.getId());

        // Create new token
        String newToken = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(newToken)
                .userId(user.getId())
                .userType(UserType.PLATFORM)
                .expiryTime(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        activationTokenRepository.save(token);

        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + newToken;
        emailService.sendPlatformActivationEmail(user.getEmail(), user.getFullName(), activationLink);

        log.info("Activation email resent to: {}", email);
    }

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public PlatformUserResponse getUserById(UUID id) {
        log.debug("Fetching platform user by id: {}", id);

        PlatformUser user = platformUserRepository.findByIdWithRolesAndPermissions(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        return toResponse(user);
    }

    /**
     * Get user by email
     */
    @Transactional(readOnly = true)
    public PlatformUserResponse getUserByEmail(String email) {
        log.debug("Fetching platform user by email: {}", email);

        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));

        return toResponse(user);
    }

    /**
     * Get all users (paginated)
     */
    @Transactional(readOnly = true)
    public Page<PlatformUserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching all platform users");

        return platformUserRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * Update user details
     */
    @Transactional
    public PlatformUserResponse updateUser(UUID id, PlatformUserUpdateRequest request) {
        log.info("Updating platform user: {}", id);

        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation());
        }

        PlatformUser updatedUser = platformUserRepository.save(user);
        log.info("Platform user updated: {}", id);

        return toResponse(updatedUser);
    }

    /**
     * Update user roles
     */
    @Transactional
    public void updateUserRoles(UUID userId, Set<Long> roleIds) {
        log.info("Updating roles for platform user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(roleIds));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be assigned");
        }

        user.setRoles(roles);
        platformUserRepository.save(user);
        log.info("Roles updated for user: {}", userId);
    }

    /**
     * Deactivate user
     */
    @Transactional
    public void deactivateUser(UUID id) {
        log.info("Deactivating platform user: {}", id);

        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        user.setActive(false);
        user.setStatus(PlatformUserStatus.INACTIVE);
        platformUserRepository.save(user);
        log.info("Platform user deactivated: {}", id);
    }

    /**
     * Activate user by admin
     */
    @Transactional
    public void activateUserByAdmin(UUID id) {
        log.info("Activating platform user by admin: {}", id);

        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        user.setActive(true);
        user.setStatus(PlatformUserStatus.ACTIVE);
        platformUserRepository.save(user);
        log.info("Platform user activated by admin: {}", id);
    }

    /**
     * Soft delete user
     */
    @Transactional
    public void deleteUser(UUID id) {
        log.info("Soft deleting platform user: {}", id);

        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        user.softDelete();
        platformUserRepository.save(user);
        activationTokenRepository.deleteByUserId(user.getId());

        log.info("Platform user soft deleted: {}", id);
    }

    /**
     * Get user statistics
     */
    @Transactional(readOnly = true)
    public PlatformUserStatistics getUserStatistics() {
        log.debug("Fetching platform user statistics");

        return PlatformUserStatistics.builder()
                .totalUsers(platformUserRepository.count())
                .activeUsers(platformUserRepository.countByActiveTrue())
                .inactiveUsers(platformUserRepository.countByActiveFalse())
                .pendingVerification(platformUserRepository.countByStatus(PlatformUserStatus.PENDING_VERIFICATION))
                .lockedUsers(platformUserRepository.countByStatus(PlatformUserStatus.LOCKED))
                .build();
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new RuntimeException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new RuntimeException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new RuntimeException("Password must contain at least one number");
        }
        if (!password.matches(".*[@#$%^&+=!].*")) {
            throw new RuntimeException("Password must contain at least one special character");
        }
    }

    private PlatformUserResponse toResponse(PlatformUser user) {
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
                        .collect(java.util.stream.Collectors.toSet()))
                .build();
    }

    private PlatformUserResponse toResponse(PlatformUser user, String invitationLink, LocalDateTime expiryTime) {
        PlatformUserResponse response = toResponse(user);
        response.setInvitationLink(invitationLink);
        response.setInvitationExpiryAt(expiryTime);
        return response;
    }
    /**
     * Force change password for user (Admin operation)
     */
    @Transactional
    public void forceChangePassword(UUID id, String newPassword, boolean mustChangeOnLogin) {
        log.info("Force changing password for platform user: {}", id);

        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        validatePasswordStrength(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(mustChangeOnLogin);

        platformUserRepository.save(user);
        log.info("Password force changed for user: {}", id);
    }
}