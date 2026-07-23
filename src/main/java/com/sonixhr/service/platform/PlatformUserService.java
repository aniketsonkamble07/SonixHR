package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformUserCreateRequest;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformUserStatistics;
import com.sonixhr.dto.platform.PlatformUserUpdateRequest;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.ValidationException;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.ActivationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PlatformUserService {

    private static final java.util.regex.Pattern UPPERCASE_PAT = java.util.regex.Pattern.compile("[A-Z]");
    private static final java.util.regex.Pattern LOWERCASE_PAT = java.util.regex.Pattern.compile("[a-z]");
    private static final java.util.regex.Pattern DIGIT_PAT = java.util.regex.Pattern.compile("\\d");
    private static final java.util.regex.Pattern SPECIAL_PAT = java.util.regex.Pattern.compile("[@#$%^&+=!]");

    private final PlatformUserRepository platformUserRepository;
    private final PlatformRoleRepository platformRoleRepository;
    private final ActivationTokenService activationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final PlatformNotificationService notificationService;
    private final PlatformUserCacheEvictionService platformUserCacheEvictionService;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    // =====================================================
    // CREATE & ACTIVATION
    // =====================================================

    @Transactional
    @CacheEvict(value = {"platformUsers", "platformUsersPage", "platformStatistics"}, allEntries = true)
    public PlatformUserResponse createUser(PlatformUserCreateRequest request, Long createdBy) {
        log.info("Creating platform user: {}", request.getEmail());

        if (platformUserRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("email", "Email address already registered");
        }

        Set<Long> roleIds = request.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(roleIds));
        if (roles.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }
        if (roles.size() != roleIds.size()) {
            Set<Long> found = roles.stream().map(PlatformRole::getId).collect(Collectors.toSet());
            Set<Long> missing = roleIds.stream()
                    .filter(id -> !found.contains(id)).collect(Collectors.toSet());
            throw new BusinessException("Invalid role IDs: " + missing);
        }

        PlatformUser user = PlatformUser.builder()
                .email(request.getEmail())
                .password("TEMPORARY_DISABLED")
                .fullName(request.getFullName())
                .designation(request.getDesignation())
                .status(UserStatus.PENDING_VERIFICATION)
                .rolesVersion(1)
                .roles(roles)
                .build();

        PlatformUser saved = platformUserRepository.save(user);

        String token = activationTokenService.generateTokenForPlatformUser(saved.getId());
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + token;

        notificationService.sendActivationEmail(saved.getEmail(), saved.getFullName(), activationLink);

        log.info("Platform user created (pending activation): {}", request.getEmail());
        return toResponse(saved, activationLink, LocalDateTime.now().plusHours(24));
    }

    // FIX: Added HttpServletRequest parameter
    @Transactional
    public PlatformUser activateUser(String token, String password, String confirmPassword, HttpServletRequest request) {
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }
        validatePasswordStrength(password);
        return activationTokenService.activatePlatformUser(token, password, request);
    }

    @Transactional
    public void forgotPassword(String email) {
        Optional<PlatformUser> userOpt = platformUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Forgot-password request for unknown email (silently ignored): {}", email);
            return;
        }

        PlatformUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Forgot-password request for non-active user (silently ignored): {}", email);
            return;
        }

        String resetToken = activationTokenService.generatePasswordResetTokenForPlatformUser(user.getId());
        String resetLink = baseUrl + "/api/platform/auth/reset-password?token=" + resetToken;
        notificationService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
    }

    // FIX: Added HttpServletRequest parameter
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("Passwords do not match");
        }
        validatePasswordStrength(newPassword);
        activationTokenService.resetPasswordForPlatformUser(token, newPassword, request);
    }

    @Transactional
    public void resendActivationEmail(String email) {
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("User is already activated");
        }
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException("User is not in pending verification status: " + user.getStatus());
        }

        String newToken = activationTokenService.generateTokenForPlatformUser(user.getId());
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + newToken;
        notificationService.sendActivationEmail(user.getEmail(), user.getFullName(), activationLink);
        log.info("Activation email resent to: {}", email);
    }

    // =====================================================
    // READ
    // =====================================================

    @Cacheable(value = "platformUsers", key = "'user:' + #userId", unless = "#result == null")
    public PlatformUserResponse getUserById(@NonNull Long userId) {
        log.debug("Loading platform user from DB: {}", userId);
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    @Cacheable(value = "platformUsers", key = "'email:' + #email", unless = "#result == null")
    public PlatformUserResponse getUserByEmail(String email) {
        log.debug("Loading platform user by email from DB: {}", email);
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    @Cacheable(
            value = "platformUsersPage",
            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort",
            unless = "#result == null || #result.content().isEmpty()"
    )
    public PageResult<PlatformUserResponse> getAllUsers(Pageable pageable) {
        log.debug("Loading platform users page {} from DB", pageable.getPageNumber());
        var page = platformUserRepository.findAll(pageable).map(this::toResponse);
        return new PageResult<>(page.getContent(), page.getTotalElements(), page.getTotalPages());
    }

    @Cacheable(value = "platformStatistics", key = "'userStats'", unless = "#result == null")
    public PlatformUserStatistics getUserStatistics() {
        log.debug("Loading user statistics from DB");
        return PlatformUserStatistics.builder()
                .totalUsers(platformUserRepository.count())
                .activeUsers(platformUserRepository.countByStatus(UserStatus.ACTIVE))
                .inactiveUsers(platformUserRepository.countByStatus(UserStatus.INACTIVE))
                .pendingVerification(platformUserRepository.countByStatus(UserStatus.PENDING_VERIFICATION))
                .suspendedUsers(platformUserRepository.countByStatus(UserStatus.SUSPENDED))
                .build();
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",     key = "'user:'  + #userId"),
            @CacheEvict(value = "platformUsersPage", allEntries = true)
    })
    public PlatformUserResponse updateUser(@NonNull Long userId, PlatformUserUpdateRequest request) {
        log.info("Updating platform user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("admin@sonixhr.com".equals(user.getEmail())) {
            throw new BusinessException("Cannot modify the default Super Admin");
        }

        boolean changed = false;

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
            changed = true;
        }
        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation());
            changed = true;
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (platformUserRepository.existsByEmail(request.getEmail())) {
                throw new ValidationException("email", "Email address already registered");
            }

            String oldEmail = user.getEmail();
            platformUserDetailsService.invalidateCache(oldEmail);
            platformUserCacheEvictionService.evictByEmailCache(oldEmail);

            user.setEmail(request.getEmail());
            changed = true;
        }

        if (!changed) return toResponse(user);

        PlatformUser updated = platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(updated.getEmail());
        platformUserCacheEvictionService.evictByEmailCache(updated.getEmail());
        return toResponse(updated);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",      key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage",  allEntries = true),
            @CacheEvict(value = "platformStatistics", allEntries = true)
    })
    public PlatformUserResponse updateUserStatus(@NonNull Long userId, UserStatus status) {
        log.info("Updating status for user {} to {}", userId, status);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("admin@sonixhr.com".equals(user.getEmail()) && status != UserStatus.ACTIVE) {
            throw new BusinessException("Cannot change status of the default Super Admin");
        }

        user.setStatus(status);
        user.setActive(status == UserStatus.ACTIVE);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        PlatformUser updated = platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(updated.getEmail());
        return toResponse(updated);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",     key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage", allEntries = true)
    })
    public PlatformUserResponse updateUserRoles(@NonNull Long userId, Set<Long> roleIds) {
        log.info("Updating roles for user: {}", userId);

        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(roleIds));
        if (roles.isEmpty()) {
            throw new BusinessException("At least one valid role must be assigned");
        }
        if (roles.size() != roleIds.size()) {
            Set<Long> found = roles.stream().map(PlatformRole::getId).collect(Collectors.toSet());
            Set<Long> missing = roleIds.stream()
                    .filter(id -> !found.contains(id)).collect(Collectors.toSet());
            throw new BusinessException("Invalid role IDs: " + missing);
        }

        user.setRoles(roles);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        PlatformUser updated = platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(updated.getEmail());
        return toResponse(updated);
    }

    // =====================================================
    // ADMIN ACTIONS
    // =====================================================

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",      key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage",  allEntries = true),
            @CacheEvict(value = "platformStatistics", allEntries = true)
    })
    public void activateUserByAdmin(@NonNull Long userId) {
        PlatformUser user = requireUser(userId);
        if ("admin@sonixhr.com".equals(user.getEmail())) {
            throw new BusinessException("Super Admin is already active");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        notificationService.sendAccountActivatedNotification(user.getEmail(), user.getFullName());
        log.info("Platform user activated by admin: {}", userId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",      key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage",  allEntries = true),
            @CacheEvict(value = "platformStatistics", allEntries = true)
    })
    public void suspendUser(@NonNull Long userId) {
        PlatformUser user = requireUser(userId);
        if ("admin@sonixhr.com".equals(user.getEmail())) {
            throw new BusinessException("Cannot suspend the default Super Admin");
        }

        user.setStatus(UserStatus.SUSPENDED);
        user.setActive(false);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        notificationService.sendAccountSuspendedNotification(user.getEmail(), user.getFullName());
        log.info("Platform user suspended: {}", userId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",      key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage",  allEntries = true),
            @CacheEvict(value = "platformStatistics", allEntries = true)
    })
    public void deleteUser(@NonNull Long userId) {
        PlatformUser user = requireUser(userId);
        if ("admin@sonixhr.com".equals(user.getEmail())) {
            throw new BusinessException("Cannot delete the default Super Admin");
        }

        user.setStatus(UserStatus.DELETED);
        user.setActive(false);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        log.info("Platform user soft-deleted: {}", userId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "platformUsers",     key = "'user:' + #userId"),
            @CacheEvict(value = "platformUsersPage", allEntries = true)
    })
    public void resetPasswordByAdmin(@NonNull Long userId, String newPassword) {
        PlatformUser user = requireUser(userId);
        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordLastChanged(LocalDateTime.now());
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        notificationService.sendPasswordResetNotification(user.getEmail(), user.getFullName());
        log.info("Password reset by admin for user: {}", userId);
    }

    // =====================================================
    // CACHE MANAGEMENT
    // =====================================================

    @Caching(evict = {
            @CacheEvict(value = "platformUsers",      allEntries = true),
            @CacheEvict(value = "platformUsersPage",  allEntries = true),
            @CacheEvict(value = "platformStatistics", allEntries = true)
    })
    public void clearAllCaches() {
        platformUserDetailsService.clearAllCaches();
        log.info("Cleared all platform user caches");
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private PlatformUser requireUser(@NonNull Long userId) {
        return platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters long");
        }
        if (!UPPERCASE_PAT.matcher(password).find()) {
            throw new BusinessException("Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE_PAT.matcher(password).find()) {
            throw new BusinessException("Password must contain at least one lowercase letter");
        }
        if (!DIGIT_PAT.matcher(password).find()) {
            throw new BusinessException("Password must contain at least one number");
        }
        if (!SPECIAL_PAT.matcher(password).find()) {
            throw new BusinessException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }

    private PlatformUserResponse toResponse(PlatformUser user) {
        if (user == null) return null;
        return PlatformUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .status(user.getStatus())
                .isActive(user.getStatus() == UserStatus.ACTIVE)
                .lastLoginAt(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles() == null ? Collections.emptySet() : user.getRoles().stream()
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

    // =====================================================
    // SUPPORTING TYPES
    // =====================================================

    public record PageResult<T>(
            List<T> content,
            long totalElements,
            int totalPages
    ) implements java.io.Serializable {}
}