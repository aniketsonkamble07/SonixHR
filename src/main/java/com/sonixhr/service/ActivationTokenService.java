package com.sonixhr.service;

import com.sonixhr.entity.ActivationToken;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.UserStatus;
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

    public static final String TOKEN_TYPE_ACTIVATION = "ACTIVATION";
    public static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    public static final String USER_TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String USER_TYPE_PLATFORM = "PLATFORM";

    // =====================================================
    // EMPLOYEE ACTIVATION METHODS
    // =====================================================

    @Transactional
    public String generateTokenForEmployee(Long employeeId) {
        log.info("Generating activation token for employee: {}", employeeId);

        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(
                employeeId, TOKEN_TYPE_ACTIVATION);

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

    @Transactional(readOnly = true)
    public boolean isTokenExpired(String token) {
        return activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .isEmpty();
    }

    @Transactional
    public Employee activateEmployee(String token, String password) {
        log.info("Activating employee with token: {}", token);

        ActivationToken activationToken = activationTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Invalid or expired activation token"));

        if (!USER_TYPE_EMPLOYEE.equals(activationToken.getUserType()) ||
                !TOKEN_TYPE_ACTIVATION.equals(activationToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        Employee employee = employeeRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new BusinessException("Employee not found"));

        if (employee.isActive()) {
            throw new BusinessException("Employee account is already activated");
        }

        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);

        Employee savedEmployee = employeeRepository.save(employee);

        Tenant tenant = employee.getTenant();
        if (tenant != null && !tenant.getIsActive()) {
            tenant.activate();
            tenantRepository.save(tenant);
            log.info("Tenant activated: {}", tenant.getSubdomain());
        }

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        activationTokenRepository.markAllUserTokensAsUsed(employee.getId(), TOKEN_TYPE_ACTIVATION);

        log.info("Employee activated successfully: {}", employee.getEmail());
        return savedEmployee;
    }

    @Transactional
    public void setPassword(String token, String newPassword) {
        Employee employee = activateEmployee(token, newPassword);
        log.info("Password set successfully for employee: {}", employee.getEmail());
    }

    // =====================================================
    // PLATFORM USER ACTIVATION METHODS (SIMPLIFIED)
    // =====================================================

    @Transactional
    public String generateTokenForPlatformUser(Long platformUserId) {
        log.info("Generating activation token for platform user: {}", platformUserId);

        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(
                platformUserId, TOKEN_TYPE_ACTIVATION);

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

        if (!USER_TYPE_PLATFORM.equals(activationToken.getUserType()) ||
                !TOKEN_TYPE_ACTIVATION.equals(activationToken.getTokenType())) {
            throw new BusinessException("Invalid token type");
        }

        PlatformUser platformUser = platformUserRepository.findById(activationToken.getUserId())
                .orElseThrow(() -> new BusinessException("Platform user not found"));

        // ✅ Simplified: Only check status (SUSPENDED is the only restriction)
        if (platformUser.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("Platform user account is already activated");
        }

        // ✅ Simplified: Only update password and status
        platformUser.setPassword(passwordEncoder.encode(password));
        platformUser.setStatus(UserStatus.ACTIVE);

        PlatformUser savedUser = platformUserRepository.save(platformUser);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        activationTokenRepository.markAllUserTokensAsUsed(platformUser.getId(), TOKEN_TYPE_ACTIVATION);

        log.info("Platform user activated successfully: {}", platformUser.getEmail());
        return savedUser;
    }

    @Transactional
    public void setPasswordForPlatformUser(String token, String newPassword) {
        activatePlatformUser(token, newPassword);
        log.info("Password set for platform user using token: {}", token);
    }

    // =====================================================
    // PLATFORM USER PASSWORD RESET METHODS (SIMPLIFIED)
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForPlatformUser(Long platformUserId) {
        log.info("Generating password reset token for platform user: {}", platformUserId);

        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(
                platformUserId, TOKEN_TYPE_PASSWORD_RESET);

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

        // ✅ Simplified: Just update password
        platformUser.setPassword(passwordEncoder.encode(newPassword));
        platformUserRepository.save(platformUser);

        resetToken.setUsed(true);
        activationTokenRepository.save(resetToken);
        activationTokenRepository.markAllUserTokensAsUsed(platformUser.getId(), TOKEN_TYPE_PASSWORD_RESET);

        log.info("Password reset successfully for platform user: {}", platformUser.getEmail());
    }

    // =====================================================
    // EMPLOYEE PASSWORD RESET METHODS
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForEmployee(Long employeeId) {
        log.info("Generating password reset token for employee: {}", employeeId);

        activationTokenRepository.deleteByUserIdAndTokenTypeAndUsedFalse(
                employeeId, TOKEN_TYPE_PASSWORD_RESET);

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

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);

        resetToken.setUsed(true);
        activationTokenRepository.save(resetToken);
        activationTokenRepository.markAllUserTokensAsUsed(employee.getId(), TOKEN_TYPE_PASSWORD_RESET);

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