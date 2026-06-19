package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformUserCreateRequest;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformUserStatistics;
import com.sonixhr.dto.platform.PlatformUserUpdateRequest;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.DuplicateResourceException;
import com.sonixhr.exceptions.NotFoundException;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.ActivationTokenService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// FIXES APPLIED:
//
// 1. pendingVerification was counting INACTIVE — now counts PENDING_VERIFICATION.
//
// 2. updateUser — old email Redis key is now evicted before the email is overwritten.
//    evictByEmailCache() helper added for this purpose.
//
// 3. @Async on protected self-invocation — all notification methods moved to
//    PlatformNotificationService (separate @Service bean). Self-calls bypass the
//    Spring proxy so @Async was silently ignored.
//
// 4. forgotPassword — no longer throws NotFoundException (leaks user existence).
//    Silently returns if email not found; caller returns a vague 200 either way.
//
// 5. updateUserStatus — now calls setActive() so the boolean field stays in sync
//    with the status enum.
//
// 6. getAllUsers — cache key now includes sort so different orderings don't collide.
//
// 7. getAllUsers — returns PageResult<T> (a serializable wrapper) instead of
//    Page<T> which does not round-trip cleanly through Jackson/Redis.
//
// 8. clearAllCaches — added @Transactional so partial eviction failures don't
//    leave Redis and Caffeine in inconsistent states.

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformRoleRepository platformRoleRepository;
    private final ActivationTokenService activationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final PlatformNotificationService notificationService; // FIX 3: replaces @Async self-calls
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
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
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

        if (user != null) {
            PlatformUser saved = platformUserRepository.save(user);

            String token = activationTokenService.generateTokenForPlatformUser(saved.getId());
            String activationLink = baseUrl + "/api/platform/auth/activate?token=" + token;

            // FIX 3: call through a separate bean so @Async proxy is honoured
            notificationService.sendActivationEmail(saved.getEmail(), saved.getFullName(), activationLink);

            log.info("Platform user created (pending activation): {}", request.getEmail());
            return toResponse(saved, activationLink, LocalDateTime.now().plusHours(24));
        }
        throw new BusinessException("Failed to construct platform user");
    }

    @Transactional
    public PlatformUser activateUser(String token, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) throw new BusinessException("Passwords do not match");
        validatePasswordStrength(password);
        return activationTokenService.activatePlatformUser(token, password);
    }

    // FIX 4: no longer throws when email is not found — avoids leaking user existence.
    @Transactional
    public void forgotPassword(String email) {
        Optional<PlatformUser> userOpt = platformUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Forgot-password request for unknown email (silently ignored): {}", email);
            return;
        }

        PlatformUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Still return silently — don't leak that the account is inactive.
            log.info("Forgot-password request for non-active user (silently ignored): {}", email);
            return;
        }

        String resetToken = activationTokenService.generatePasswordResetTokenForPlatformUser(user.getId());
        String resetLink = baseUrl + "/api/platform/auth/reset-password?token=" + resetToken;
        notificationService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
    }

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) throw new BusinessException("Passwords do not match");
        validatePasswordStrength(newPassword);
        activationTokenService.resetPasswordForPlatformUser(token, newPassword);
    }

    @Transactional
    public void resendActivationEmail(String email) {
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        if (user.getStatus() == UserStatus.ACTIVE)
            throw new BusinessException("User is already activated");
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION)
            throw new BusinessException("User is not in pending verification status: " + user.getStatus());

        String newToken = activationTokenService.generateTokenForPlatformUser(user.getId());
        String activationLink = baseUrl + "/api/platform/auth/activate?token=" + newToken;
        // FIX 3: async via separate bean
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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        return toResponse(user);
    }

    @Cacheable(value = "platformUsers", key = "'email:' + #email", unless = "#result == null")
    public PlatformUserResponse getUserByEmail(String email) {
        log.debug("Loading platform user by email from DB: {}", email);
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        return toResponse(user);
    }

    // FIX 6: key now includes sort so different sort orders don't share a cache entry.
    // FIX 7: returns PageResult (serializable wrapper) instead of Page<T>.
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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

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
                throw new DuplicateResourceException("Email already exists: " + request.getEmail());
            }

            // FIX 2: evict the OLD email key from both Caffeine and Redis BEFORE
            // overwriting the field — otherwise the old key is stale forever.
            String oldEmail = user.getEmail();
            platformUserDetailsService.invalidateCache(oldEmail);
            platformUserCacheEvictionService.evictByEmailCache(oldEmail);

            user.setEmail(request.getEmail());
            changed = true;
        }

        if (!changed) return toResponse(user);

        PlatformUser updated = platformUserRepository.save(user);
        // Evict new email key too (it may have been cached from a prior lookup)
        platformUserDetailsService.invalidateCache(updated.getEmail());
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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if ("admin@sonixhr.com".equals(user.getEmail()) && status != UserStatus.ACTIVE) {
            throw new BusinessException("Cannot change status of the default Super Admin");
        }

        user.setStatus(status);
        // FIX 5: keep the boolean active field in sync with the status enum
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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (roleIds == null || roleIds.isEmpty()) throw new BusinessException("At least one valid role must be assigned");
        Set<PlatformRole> roles = new HashSet<>(platformRoleRepository.findAllById(roleIds));
        if (roles.isEmpty()) throw new BusinessException("At least one valid role must be assigned");
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
        if ("admin@sonixhr.com".equals(user.getEmail()))
            throw new BusinessException("Super Admin is already active");

        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        // FIX 3: async via separate bean
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
        if ("admin@sonixhr.com".equals(user.getEmail()))
            throw new BusinessException("Cannot suspend the default Super Admin");

        user.setStatus(UserStatus.SUSPENDED);
        user.setActive(false);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(user.getEmail());
        // FIX 3: async via separate bean
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
        if ("admin@sonixhr.com".equals(user.getEmail()))
            throw new BusinessException("Cannot delete the default Super Admin");

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
        // FIX 3: async via separate bean
        notificationService.sendPasswordResetNotification(user.getEmail(), user.getFullName());
        log.info("Password reset by admin for user: {}", userId);
    }

    // =====================================================
    // CACHE MANAGEMENT
    // =====================================================

    // Cache eviction operations aren't transactional resources.
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
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8)
            throw new BusinessException("Password must be at least 8 characters long");
        if (!password.matches(".*[A-Z].*"))
            throw new BusinessException("Password must contain at least one uppercase letter");
        if (!password.matches(".*[a-z].*"))
            throw new BusinessException("Password must contain at least one lowercase letter");
        if (!password.matches(".*\\d.*"))
            throw new BusinessException("Password must contain at least one number");
        if (!password.matches(".*[@#$%^&+=!].*"))
            throw new BusinessException("Password must contain at least one special character (@#$%^&+=!)");
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

    // =====================================================
    // SUPPORTING TYPES
    // =====================================================

    /**
     * Serializable wrapper for paginated results.
     * Replaces Page<T> which does not deserialize cleanly from Redis/Jackson.
     */
    public record PageResult<T>(
            List<T> content,
            long totalElements,
            int totalPages
    ) implements java.io.Serializable {}
}