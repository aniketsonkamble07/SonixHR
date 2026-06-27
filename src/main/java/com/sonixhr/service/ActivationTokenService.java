package com.sonixhr.service;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ActivationTokenService {

    private final EmployeeRepository employeeRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.activation.token.expiry-hours:24}")
    private long tokenExpiryHours;

    @Value("${app.reset.token.expiry-hours:1}")
    private long resetTokenExpiryHours;

    // Fallback in-memory token store (used when Redis is down)
    private final Map<String, TokenInfo> fallbackTokenStore = new java.util.concurrent.ConcurrentHashMap<>();

    private static class TokenInfo {
        final String value;
        final long expiryTime;

        TokenInfo(String value, long ttlHours) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(ttlHours);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private void setToken(String key, String value, long ttlHours) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis is down, storing token in-memory. Error: {}", e.getMessage());
            fallbackTokenStore.put(key, new TokenInfo(value, ttlHours));
        }
    }

    private String getToken(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis is down, retrieving token from in-memory fallback. Error: {}", e.getMessage());
            TokenInfo info = fallbackTokenStore.get(key);
            if (info != null) {
                if (info.isExpired()) {
                    fallbackTokenStore.remove(key);
                    return null;
                }
                return info.value;
            }
            return null;
        }
    }

    private boolean hasToken(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis is down, checking token in-memory fallback. Error: {}", e.getMessage());
            TokenInfo info = fallbackTokenStore.get(key);
            if (info != null) {
                if (info.isExpired()) {
                    fallbackTokenStore.remove(key);
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    private void deleteToken(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis is down, deleting token from in-memory fallback. Error: {}", e.getMessage());
            fallbackTokenStore.remove(key);
        }
    }

    // Redis key prefixes
    private static final String REDIS_PREFIX_EMPLOYEE_ACTIVATION = "activation:employee:";
    private static final String REDIS_PREFIX_EMPLOYEE_RESET = "reset:employee:";
    private static final String REDIS_PREFIX_PLATFORM_ACTIVATION = "activation:platform:";
    private static final String REDIS_PREFIX_PLATFORM_RESET = "reset:platform:";

    // =====================================================
    // EMPLOYEE (TENANT) ACTIVATION
    // =====================================================

    @Transactional
    public String generateTokenForEmployee(Long employeeId) {
        log.info("Generating activation token for employee: {}", employeeId);

        String token = UUID.randomUUID().toString();
        String key = REDIS_PREFIX_EMPLOYEE_ACTIVATION + token;

        setToken(key, employeeId.toString(), tokenExpiryHours);

        log.info("Generated activation token for employee: {}", employeeId);
        return token;
    }

    /**
     * Get employee ID from activation token
     */
    public Long getEmployeeIdFromToken(String token) {
        log.debug("Getting employee ID from token: {}", token);

        String key = REDIS_PREFIX_EMPLOYEE_ACTIVATION + token;
        String employeeIdStr = getToken(key);

        if (employeeIdStr == null) {
            // Also check reset token
            key = REDIS_PREFIX_EMPLOYEE_RESET + token;
            employeeIdStr = getToken(key);
        }

        if (employeeIdStr == null) {
            throw new BusinessException("Invalid or expired token");
        }

        return Long.parseLong(employeeIdStr);
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        String[] keys = {
                REDIS_PREFIX_EMPLOYEE_ACTIVATION + token,
                REDIS_PREFIX_EMPLOYEE_RESET + token,
                REDIS_PREFIX_PLATFORM_ACTIVATION + token,
                REDIS_PREFIX_PLATFORM_RESET + token
        };

        for (String key : keys) {
            if (hasToken(key)) {
                return false; // Token exists, not expired
            }
        }
        return true; // Token not found (expired or invalid)
    }

    @Transactional
    public Employee activateEmployee(String token, String password) {
        log.info("Activating employee with token: {}", token);

        String key = REDIS_PREFIX_EMPLOYEE_ACTIVATION + token;
        String employeeIdStr = getToken(key);

        if (employeeIdStr == null) {
            throw new BusinessException("Invalid or expired activation token");
        }

        Long employeeId = Long.parseLong(employeeIdStr);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("Employee not found"));

        if (employee.isActive()) {
            throw new BusinessException("Invalid or expired activation token");
        }

        employee.setPasswordHash(passwordEncoder.encode(password));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        Employee savedEmployee = employeeRepository.save(employee);

        Tenant tenant = employee.getTenant();
        if (tenant != null && !tenant.getIsActive()) {
            tenant.activate();
            tenantRepository.save(tenant);
            log.info("Tenant activated: {}", tenant.getCompanyName());
        }

        // Delete used token
        deleteToken(key);

        log.info("Employee activated successfully: {}", employee.getEmail());
        return savedEmployee;
    }

    // =====================================================
    // PLATFORM USER ACTIVATION
    // =====================================================

    @Transactional
    public String generateTokenForPlatformUser(Long platformUserId) {
        log.info("Generating activation token for platform user: {}", platformUserId);

        String token = UUID.randomUUID().toString();
        String key = REDIS_PREFIX_PLATFORM_ACTIVATION + token;

        setToken(key, platformUserId.toString(), tokenExpiryHours);

        log.info("Generated activation token for platform user: {}", platformUserId);
        return token;
    }

    @Transactional
    public PlatformUser activatePlatformUser(String token, String password) {
        log.info("Activating platform user with token: {}", token);

        String key = REDIS_PREFIX_PLATFORM_ACTIVATION + token;
        String userIdStr = getToken(key);

        if (userIdStr == null) {
            throw new BusinessException("Invalid or expired activation token");
        }

        Long userId = Long.parseLong(userIdStr);
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Platform user not found"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("Invalid or expired activation token");
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        PlatformUser savedUser = platformUserRepository.save(user);

        // Delete used token
        deleteToken(key);

        log.info("Platform user activated successfully: {}", user.getEmail());
        return savedUser;
    }

    // =====================================================
    // PLATFORM USER PASSWORD RESET
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForPlatformUser(Long platformUserId) {
        log.info("Generating password reset token for platform user: {}", platformUserId);

        String token = UUID.randomUUID().toString();
        String key = REDIS_PREFIX_PLATFORM_RESET + token;

        setToken(key, platformUserId.toString(), resetTokenExpiryHours);

        log.info("Generated password reset token for platform user: {}", platformUserId);
        return token;
    }

    @Transactional
    public void resetPasswordForPlatformUser(String token, String newPassword) {
        log.info("Resetting password for platform user with token: {}", token);

        String key = REDIS_PREFIX_PLATFORM_RESET + token;
        String userIdStr = getToken(key);

        if (userIdStr == null) {
            throw new BusinessException("Invalid or expired reset token");
        }

        Long userId = Long.parseLong(userIdStr);
        PlatformUser user = platformUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Platform user not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordLastChanged(LocalDateTime.now());
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        platformUserRepository.save(user);

        // Delete used token
        deleteToken(key);

        log.info("Password reset successfully for platform user: {}", user.getEmail());
    }

    // =====================================================
    // EMPLOYEE PASSWORD RESET
    // =====================================================

    @Transactional
    public String generatePasswordResetTokenForEmployee(Long employeeId) {
        log.info("Generating password reset token for employee: {}", employeeId);

        String token = UUID.randomUUID().toString();
        String key = REDIS_PREFIX_EMPLOYEE_RESET + token;

        setToken(key, employeeId.toString(), resetTokenExpiryHours);

        log.info("Generated password reset token for employee: {}", employeeId);
        return token;
    }

    @Transactional
    public void resetPasswordForEmployee(String token, String newPassword) {
        log.info("Resetting password for employee with token: {}", token);

        String key = REDIS_PREFIX_EMPLOYEE_RESET + token;
        String employeeIdStr = getToken(key);

        if (employeeIdStr == null) {
            throw new BusinessException("Invalid or expired reset token");
        }

        Long employeeId = Long.parseLong(employeeIdStr);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("Employee not found"));

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        employeeRepository.save(employee);

        // Delete used token
        deleteToken(key);

        log.info("Password reset successfully for employee: {}", employee.getEmail());
    }

    // =====================================================
    // TOKEN UTILITY METHODS
    // =====================================================

    public boolean isValidToken(String token) {
        String[] patterns = {
                REDIS_PREFIX_EMPLOYEE_ACTIVATION + token,
                REDIS_PREFIX_EMPLOYEE_RESET + token,
                REDIS_PREFIX_PLATFORM_ACTIVATION + token,
                REDIS_PREFIX_PLATFORM_RESET + token
        };

        for (String pattern : patterns) {
            if (hasToken(pattern)) {
                return true;
            }
        }
        return false;
    }

    public void invalidateToken(String token) {
        String[] patterns = {
                REDIS_PREFIX_EMPLOYEE_ACTIVATION + token,
                REDIS_PREFIX_EMPLOYEE_RESET + token,
                REDIS_PREFIX_PLATFORM_ACTIVATION + token,
                REDIS_PREFIX_PLATFORM_RESET + token
        };

        for (String pattern : patterns) {
            deleteToken(pattern);
        }

        log.info("Token invalidated: {}", token);
    }

    /**
     * Get platform user ID from token
     */
    public Long getPlatformUserIdFromToken(String token) {
        log.debug("Getting platform user ID from token: {}", token);

        String key = REDIS_PREFIX_PLATFORM_ACTIVATION + token;
        String userIdStr = getToken(key);

        if (userIdStr == null) {
            key = REDIS_PREFIX_PLATFORM_RESET + token;
            userIdStr = getToken(key);
        }

        if (userIdStr == null) {
            throw new BusinessException("Invalid or expired token");
        }

        return Long.parseLong(userIdStr);
    }

    /**
     * Set password for employee using activation token
     * This is called during tenant registration flow
     */
    @Transactional
    public void setPassword(String token, String newPassword) {
        log.info("Setting password with token: {}", token);

        // Get employee ID from token
        Long employeeId = getEmployeeIdFromToken(token);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("Employee not found"));

        if (employee.isActive()) {
            throw new BusinessException("Invalid or expired activation token");
        }

        // Set password and activate
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setActive(true);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.incrementRolesVersion();
        employee.clearAuthoritiesCache();

        employeeRepository.save(employee);

        // Activate tenant if needed
        Tenant tenant = employee.getTenant();
        if (tenant != null && !tenant.getIsActive()) {
            tenant.activate();
            tenantRepository.save(tenant);
            log.info("Tenant activated: {}", tenant.getCompanyName());
        }

        // Invalidate the token
        invalidateToken(token);

        log.info("Password set successfully for employee: {}", employee.getEmail());
    }
}