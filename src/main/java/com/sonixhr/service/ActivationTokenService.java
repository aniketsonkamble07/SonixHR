package com.sonixhr.service;

import com.sonixhr.entity.ActivationToken;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.PlatformUserStatus;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.ActivationTokenRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationTokenService {

    private final ActivationTokenRepository activationTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public static final long TOKEN_EXPIRY_HOURS = 24;
    public static final long PASSWORD_RESET_EXPIRY_HOURS = 1;

    // Token type constants
    public static final String TOKEN_TYPE_ACTIVATION = "ACTIVATION";
    public static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    // User type constants
    public static final String USER_TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String USER_TYPE_PLATFORM = "PLATFORM";

    // =====================================================
    // EMPLOYEE ACTIVATION METHODS
    // =====================================================

    @Transactional
    public String generateTokenForEmployee(Long employeeId) {
        log.info("Generating activation token for employee: {}", employeeId);

        // Delete any existing unused tokens for this employee
        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(employeeId, USER_TYPE_EMPLOYEE + "_" + TOKEN_TYPE_ACTIVATION);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(employeeId)
                .userType(USER_TYPE_EMPLOYEE)
                .tokenType(TOKEN_TYPE_ACTIVATION)
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                .used(false)
                .build();
        activationTokenRepository.save(token);

        log.info("Generated activation token for employee: {}", employeeId);
        return tokenValue;
    }

    /**
     * Activate employee and return the employee object
     */
    @Transactional
    public Employee activateEmployee(String token, String password) {
        log.info("Activating employee with token: {}", token);

        // Find valid token
        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired activation token"));

        // Verify token type
        if (!USER_TYPE_EMPLOYEE.equals(activationToken.getUserType()) ||
                !TOKEN_TYPE_ACTIVATION.equals(activationToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        // Get employee
        Employee employee = employeeRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new BusinessException("Employee not found"));

        // Check if employee is already activated
        if (employee.isActive()) {
            throw new BusinessException("Employee account is already activated");
        }

        // Update employee
        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setMustChangePassword(false);

        Employee savedEmployee = employeeRepository.save(employee);

        // Activate associated tenant if needed
        Tenant tenant = tenantRepository.findById(employee.getTenantId()).orElse(null);
        if (tenant != null && !tenant.getIsActive()) {
            tenant.activate();
            tenantRepository.save(tenant);
            log.info("Tenant activated: {}", tenant.getSubdomain());
        }

        // Mark token as used
        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        // Optional: Mark all other activation tokens for this employee as used
        activationTokenRepository.markAllUserTokensAsUsed(employee.getId(), USER_TYPE_EMPLOYEE + "_" + TOKEN_TYPE_ACTIVATION);

        log.info("Employee activated successfully: {}", employee.getEmail());
        return savedEmployee;
    }

    /**
     * Set password for employee (legacy method)
     */
    @Transactional
    public void setPassword(String token, String newPassword) {
        Employee employee = activateEmployee(token, newPassword);
        log.info("Password set successfully for employee: {}", employee.getEmail());
    }

    // =====================================================
    // PLATFORM USER ACTIVATION METHODS
    // =====================================================

    @Transactional
    public String generateTokenForPlatformUser(Long platformUserId) {
        log.info("Generating activation token for platform user: {}", platformUserId);

        // Delete any existing unused tokens for this platform user
        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(platformUserId, USER_TYPE_PLATFORM + "_" + TOKEN_TYPE_ACTIVATION);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(platformUserId)
                .userType(USER_TYPE_PLATFORM)
                .tokenType(TOKEN_TYPE_ACTIVATION)
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                .used(false)
                .build();
        activationTokenRepository.save(token);

        log.info("Generated activation token for platform user: {}", platformUserId);
        return tokenValue;
    }

    @Transactional
    public PlatformUser activatePlatformUser(String token, String password) {
        log.info("Activating platform user with token: {}", token);

        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired activation token"));

        // Verify token type
        if (!USER_TYPE_PLATFORM.equals(activationToken.getUserType()) ||
                !TOKEN_TYPE_ACTIVATION.equals(activationToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        PlatformUser platformUser = platformUserRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new BusinessException("Platform user not found"));

        // Check if user is already activated
        if (platformUser.isEnabled() && platformUser.isActive()) {
            throw new BusinessException("Platform user account is already activated");
        }

        // Update platform user
        platformUser.setPassword(passwordEncoder.encode(password));
        platformUser.setActive(true);
        platformUser.setEnabled(true);
        platformUser.setStatus(PlatformUserStatus.ACTIVE);
        platformUser.setMustChangePassword(false);
        platformUser.setPasswordLastChanged(LocalDateTime.now());
        platformUser.setFailedLoginAttempts(0);
        platformUser.setAccountNonLocked(true);

        PlatformUser savedUser = platformUserRepository.save(platformUser);

        // Mark token as used
        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        // Optional: Mark all other activation tokens for this user as used
        activationTokenRepository.markAllUserTokensAsUsed(platformUser.getId(), USER_TYPE_PLATFORM + "_" + TOKEN_TYPE_ACTIVATION);

        log.info("Platform user activated successfully: {}", platformUser.getEmail());
        return savedUser;
    }

    @Transactional
    public void setPasswordForPlatformUser(String token, String newPassword) {
        activatePlatformUser(token, newPassword);
        log.info("Password set for platform user using token: {}", token);
    }

    // =====================================================
    // PLATFORM USER PASSWORD RESET METHODS
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForPlatformUser(Long platformUserId) {
        log.info("Generating password reset token for platform user: {}", platformUserId);

        // Delete any existing unused reset tokens
        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(platformUserId, USER_TYPE_PLATFORM + "_" + TOKEN_TYPE_PASSWORD_RESET);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(platformUserId)
                .userType(USER_TYPE_PLATFORM)
                .tokenType(TOKEN_TYPE_PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_EXPIRY_HOURS))
                .used(false)
                .build();
        activationTokenRepository.save(token);

        log.info("Generated password reset token for platform user: {}", platformUserId);
        return tokenValue;
    }

    @Transactional
    public void resetPasswordForPlatformUser(String token, String newPassword) {
        log.info("Resetting password for platform user with token: {}", token);

        ActivationToken resetToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        if (!USER_TYPE_PLATFORM.equals(resetToken.getUserType()) ||
                !TOKEN_TYPE_PASSWORD_RESET.equals(resetToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        PlatformUser platformUser = platformUserRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException("Platform user not found"));

        // Update password
        platformUser.setPassword(passwordEncoder.encode(newPassword));
        platformUser.setPasswordLastChanged(LocalDateTime.now());
        platformUser.setMustChangePassword(true); // Force password change on next login
        platformUser.setFailedLoginAttempts(0);
        platformUser.setAccountNonLocked(true);

        platformUserRepository.save(platformUser);

        // Mark token as used
        resetToken.setUsed(true);
        activationTokenRepository.save(resetToken);

        // Mark all other password reset tokens for this user as used
        activationTokenRepository.markAllUserTokensAsUsed(platformUser.getId(), USER_TYPE_PLATFORM + "_" + TOKEN_TYPE_PASSWORD_RESET);

        log.info("Password reset successfully for platform user: {}", platformUser.getEmail());
    }

    // =====================================================
    // EMPLOYEE PASSWORD RESET METHODS
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForEmployee(Long employeeId) {
        log.info("Generating password reset token for employee: {}", employeeId);

        // Delete any existing unused reset tokens
        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(employeeId, USER_TYPE_EMPLOYEE + "_" + TOKEN_TYPE_PASSWORD_RESET);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(employeeId)
                .userType(USER_TYPE_EMPLOYEE)
                .tokenType(TOKEN_TYPE_PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_EXPIRY_HOURS))
                .used(false)
                .build();
        activationTokenRepository.save(token);

        log.info("Generated password reset token for employee: {}", employeeId);
        return tokenValue;
    }

    @Transactional
    public void resetPasswordForEmployee(String token, String newPassword) {
        log.info("Resetting password for employee with token: {}", token);

        ActivationToken resetToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        if (!USER_TYPE_EMPLOYEE.equals(resetToken.getUserType()) ||
                !TOKEN_TYPE_PASSWORD_RESET.equals(resetToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        Employee employee = employeeRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException("Employee not found"));

        // Update password - Employee uses setPasswordHash
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setMustChangePassword(true);
        employeeRepository.save(employee);

        // Mark token as used
        resetToken.setUsed(true);
        activationTokenRepository.save(resetToken);

        // Mark all other password reset tokens for this employee as used
        activationTokenRepository.markAllUserTokensAsUsed(employee.getId(), USER_TYPE_EMPLOYEE + "_" + TOKEN_TYPE_PASSWORD_RESET);

        log.info("Password reset successfully for employee: {}", employee.getEmail());
    }

    // =====================================================
    // TOKEN UTILITY METHODS
    // =====================================================

    @Transactional(readOnly = true)
    public Long getPlatformUserIdFromToken(String token) {
        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));

        if (!USER_TYPE_PLATFORM.equals(activationToken.getUserType())) {
            throw new BusinessException("Token is not for platform user");
        }

        return activationToken.getUserId();
    }

    @Transactional(readOnly = true)
    public Long getEmployeeIdFromToken(String token) {
        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));

        if (!USER_TYPE_EMPLOYEE.equals(activationToken.getUserType())) {
            throw new BusinessException("Token is not for employee");
        }

        return activationToken.getUserId();
    }

    @Transactional
    public void invalidateToken(String token) {
        int updated = activationTokenRepository.markAsUsed(token);
        if (updated > 0) {
            log.info("Token invalidated: {}", token);
        } else {
            log.warn("Token not found or already used: {}", token);
        }
    }

    @Transactional(readOnly = true)
    public boolean isValidToken(String token) {
        return activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .isPresent();
    }

    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = activationTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Cleaned up {} expired tokens", deleted);
        return deleted;
    }
}