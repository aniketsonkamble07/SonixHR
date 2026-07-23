// service/tenant/TenantAuthService.java
package com.sonixhr.service.tenant;

import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.employee.EmployeePasswordHistory;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TenantDataStatus;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.enums.employee.EmployeeStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeePasswordHistoryRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.TokenBlacklistService;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
import com.sonixhr.service.employee.EmployeeDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TenantAuthService {

    private final AuthenticationManager tenantAuthenticationManager;
    private final EmployeeRepository employeeRepository;
    private final EmployeePasswordHistoryRepository passwordHistoryRepository;
    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmployeeDetailsService employeeDetailsService;
    private final ActivationTokenService activationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.password.history.enabled:true}")
    private boolean passwordHistoryEnabled;

    @Value("${app.password.history.count:5}")
    private int passwordHistoryCount;

    public TenantAuthService(
            @Qualifier("tenantAuthenticationManager") AuthenticationManager tenantAuthenticationManager,
            EmployeeRepository employeeRepository,
            EmployeePasswordHistoryRepository passwordHistoryRepository,
            TenantRepository tenantRepository,
            TenantSubscriptionRepository subscriptionRepository,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            EmployeeDetailsService employeeDetailsService,
            ActivationTokenService activationTokenService,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.tenantAuthenticationManager = tenantAuthenticationManager;
        this.employeeRepository = employeeRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.employeeDetailsService = employeeDetailsService;
        this.activationTokenService = activationTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // =====================================================
    // FIND EMPLOYEE
    // =====================================================

    public Employee findEmployeeByEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        return employeeDetailsService.findByEmail(email).orElse(null);
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Transactional
    public LoginResponse login(String email, String password) {
        log.info("Login attempt for employee: {}", email);

        try {
            Optional<Employee> employeeOpt = employeeDetailsService.findByEmail(email);
            if (employeeOpt.isEmpty()) {
                log.warn("Login failed: Employee not found - {}", email);
                throw new BadCredentialsException("Invalid email or password");
            }

            Employee employee = employeeOpt.get();

            if (!employee.isCredentialsNonExpired()) {
                log.info("Password change required for employee: {}", email);
                String resetToken = jwtService.generatePasswordResetToken(employee);

                return LoginResponse.builder()
                        .success(false)
                        .message("Password change required. Please reset your password before logging in.")
                        .errorCode("AUTH_004")
                        .requiresPasswordChange(true)
                        .resetToken(resetToken)
                        .changePasswordUrl(baseUrl + "/api/tenant/auth/change-password")
                        .email(employee.getEmail())
                        .fullName(employee.getFullName())
                        .userId(employee.getId())
                        .build();
            }

            Authentication auth = tenantAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            Employee authenticatedEmployee = (Employee) auth.getPrincipal();

            if (authenticatedEmployee.getStatus() == EmployeeStatus.INACTIVE) {
                throw new BadCredentialsException("Account not activated. Please activate your account using the activation link sent to your email.");
            }
            if (authenticatedEmployee.getStatus() == EmployeeStatus.INVITED) {
                throw new BadCredentialsException("Account pending activation. Please check your email for the activation link.");
            }
            if (authenticatedEmployee.getStatus() == EmployeeStatus.RESIGNED) {
                throw new BadCredentialsException("Your account has been resigned. Please contact your administrator.");
            }
            if (authenticatedEmployee.getStatus() == EmployeeStatus.TERMINATED) {
                throw new BadCredentialsException("Your account has been terminated. Please contact your administrator.");
            }
            if (authenticatedEmployee.getStatus() == EmployeeStatus.ON_LEAVE) {
                throw new BadCredentialsException("Your account is currently on leave. Please contact your administrator.");
            }
            if (!authenticatedEmployee.isActive()) {
                throw new BadCredentialsException("Account is inactive. Please contact your administrator.");
            }

            validateTenantStatus(authenticatedEmployee);

            authenticatedEmployee.setLastLoginAt(LocalDateTime.now());
            employeeRepository.save(authenticatedEmployee);
            employeeDetailsService.invalidateEmployeeCache(authenticatedEmployee.getEmail());

            var tokenPair = jwtService.generateEmployeeTokenPair(authenticatedEmployee);
            log.info("Employee logged in: {}", email);

            return LoginResponse.builder()
                    .success(true)
                    .message("Login successful")
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType("Bearer")
                    .expiresIn(tokenPair.getExpiresIn())
                    .email(authenticatedEmployee.getEmail())
                    .fullName(authenticatedEmployee.getFullName())
                    .userId(authenticatedEmployee.getId())
                    .requiresPasswordChange(false)
                    .build();

        } catch (CredentialsExpiredException e) {
            log.info("Credentials expired for employee: {}", email);

            try {
                Optional<Employee> employeeOpt = employeeDetailsService.findByEmail(email);
                if (employeeOpt.isPresent()) {
                    Employee employee = employeeOpt.get();
                    String resetToken = jwtService.generatePasswordResetToken(employee);

                    return LoginResponse.builder()
                            .success(false)
                            .message("Password change required. Please reset your password before logging in.")
                            .errorCode("AUTH_004")
                            .requiresPasswordChange(true)
                            .resetToken(resetToken)
                            .changePasswordUrl(baseUrl + "/api/tenant/auth/change-password")
                            .email(employee.getEmail())
                            .fullName(employee.getFullName())
                            .userId(employee.getId())
                            .build();
                }
            } catch (Exception ex) {
                log.warn("Failed to generate reset token for expired credentials: {}", ex.getMessage());
            }

            throw new BadCredentialsException("Password change required. Please reset your password.");

        } catch (BadCredentialsException e) {
            log.warn("Login failed for employee: {} - {}", email, e.getMessage());
            throw e;
        } catch (DisabledException | LockedException e) {
            log.warn("Login rejected for disabled/locked employee account: {}", email);
            throw new BadCredentialsException("Account is disabled or locked", e);
        } catch (Exception e) {
            log.error("Unexpected error during login for employee: {} - {}", email, e.getMessage(), e);
            throw new BadCredentialsException("Login failed. Please try again.");
        }
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    public LoginResponse refresh(String refreshToken) {
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new BadCredentialsException("Token has been revoked");
        }

        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(refreshToken);
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Employee not found"));

        if (employee.getStatus() != EmployeeStatus.ACTIVE && employee.getStatus() != EmployeeStatus.PROBATION) {
            throw new BadCredentialsException("Account is not active");
        }
        if (!employee.isActive()) {
            throw new BadCredentialsException("Account is not active");
        }

        validateTenantStatus(employee);

        var tokenPair = jwtService.generateEmployeeTokenPair(employee);
        log.info("Token refreshed for employee: {}", email);

        return LoginResponse.builder()
                .success(true)
                .message("Token refreshed successfully")
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(employee.getEmail())
                .fullName(employee.getFullName())
                .userId(employee.getId())
                .requiresPasswordChange(false)
                .build();
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenBlacklistService.blacklistToken(token);
            log.info("Logout successful, token blacklisted");
        }
    }

    // =====================================================
    // CHANGE PASSWORD ✅
    // =====================================================

    @Transactional
    public LoginResponse changePassword(Employee currentUser, String currentPassword,
                                        String newPassword, String confirmPassword,
                                        HttpServletRequest request) {
        log.info("Changing password for employee: {}", currentUser.getEmail());

        // 1. Verify current password
        if (!passwordEncoder.matches(currentPassword, currentUser.getPasswordHash())) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Current password is incorrect")
                    .errorCode("AUTH_001")
                    .build();
        }

        // 2. Validate new password matches confirm password
        if (!newPassword.equals(confirmPassword)) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Passwords do not match")
                    .errorCode("AUTH_005")
                    .build();
        }

        // 3. Validate password strength
        try {
            validatePasswordStrength(newPassword);
        } catch (IllegalArgumentException e) {
            return LoginResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .errorCode("AUTH_005")
                    .build();
        }

        // 4. Check if new password is same as current
        if (passwordEncoder.matches(newPassword, currentUser.getPasswordHash())) {
            return LoginResponse.builder()
                    .success(false)
                    .message("New password cannot be the same as current password")
                    .errorCode("AUTH_005")
                    .build();
        }

        // 5. Check password history (prevent reuse)
        try {
            validatePasswordNotUsedBefore(currentUser, newPassword);
        } catch (BusinessException e) {
            return LoginResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .errorCode("AUTH_005")
                    .build();
        }

        // 6. Update password
        String newPasswordHash = passwordEncoder.encode(newPassword);
        currentUser.setPasswordHash(newPasswordHash);
        currentUser.setPasswordChangedAt(LocalDateTime.now());
        currentUser.setMustChangePassword(false);
        currentUser.incrementRolesVersion();
        currentUser.clearAuthoritiesCache();
        employeeRepository.save(currentUser);

        // 7. Save to password history
        savePasswordToHistory(currentUser, newPasswordHash, request);

        // 8. Invalidate cache
        employeeDetailsService.invalidateEmployeeCache(currentUser.getEmail());

        log.info("Password changed successfully for employee: {}", currentUser.getEmail());

        return LoginResponse.builder()
                .success(true)
                .message("Password changed successfully. Please log in with your new password.")
                .build();
    }

    // =====================================================
    // FORGOT PASSWORD
    // =====================================================

    @Transactional
    public void forgotPassword(String email, HttpServletRequest request) {
        log.info("Processing forgot password for: {}", email);

        try {
            Employee employee = employeeDetailsService.findByEmail(email).orElse(null);
            if (employee == null) {
                log.info("Password reset requested for non-existent email: {}", email);
                return;
            }

            String resetToken = activationTokenService.generatePasswordResetTokenForEmployee(employee.getId());
            String resetLink = baseUrl + "/api/tenant/auth/reset-password?token=" + resetToken;

            emailService.sendPasswordResetEmail(
                    employee.getEmail(),
                    employee.getFullName(),
                    resetLink
            );

            log.info("Password reset email sent to: {}", email);

        } catch (Exception e) {
            log.error("Error processing forgot password for {}: {}", email, e.getMessage());
        }
    }

    // =====================================================
    // RESET PASSWORD
    // =====================================================

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        log.info("Resetting password from email link");

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);
        activationTokenService.resetPasswordForEmployee(token, newPassword, request);

        log.info("Password reset successfully from email link");
    }

    // =====================================================
    // SET PASSWORD (Activation Flow)
    // =====================================================

    @Transactional
    public void setPassword(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        log.info("Setting password with activation token");

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);
        activationTokenService.setPassword(token, newPassword, request);

        log.info("Password set successfully");
    }

    // =====================================================
    // RESEND ACTIVATION EMAIL
    // =====================================================

    @Transactional
    public void resendActivationEmail(String email, HttpServletRequest request) {
        log.info("Resending activation email for: {}", email);

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Employee not found with email: " + email));

        if (employee.getStatus() == EmployeeStatus.ACTIVE) {
            throw new BusinessException("Account is already activated");
        }

        if (employee.getPasswordHash() != null && !employee.getPasswordHash().isEmpty()) {
            employee.setStatus(EmployeeStatus.ACTIVE);
            employee.setActive(true);
            employeeRepository.save(employee);
            throw new BusinessException("Account is already activated. Please login.");
        }

        String activationToken = activationTokenService.generateTokenForEmployee(employee.getId());

        LocalDateTime expiryTime = LocalDateTime.now().plusHours(24);
        employee.setActivationToken(activationToken);
        employee.setActivationTokenExpiry(expiryTime);
        employee.setStatus(EmployeeStatus.INVITED);
        employeeRepository.save(employee);

        try {
            String activationLink = baseUrl + "/api/tenant/auth/activate?token=" + activationToken;
            emailService.sendActivationEmail(employee.getEmail(), employee.getFullName(), activationLink);
            log.info("Activation email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send activation email to: {}", email, e);
            throw new BusinessException("Failed to send activation email. Please try again later.");
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Validate password strength
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter, " +
                            "one lowercase letter, one number, and one special character"
            );
        }
    }

    /**
     * Validate that password was not used before (prevents password reuse)
     */
    private void validatePasswordNotUsedBefore(Employee employee, String newPassword) {
        // Skip for new accounts with no password
        if (employee.getPasswordHash() == null || employee.getPasswordHash().isEmpty()) {
            return;
        }

        if (!passwordHistoryEnabled) {
            return;
        }

        // Get last N password hashes
        List<String> previousHashes = passwordHistoryRepository.findLastNPasswordHashes(
                employee.getId(),
                passwordHistoryCount
        );

        // Check if new password matches any previous password
        for (String oldHash : previousHashes) {
            if (passwordEncoder.matches(newPassword, oldHash)) {
                throw new BusinessException(
                        "Password was used recently. Please choose a different password."
                );
            }
        }
    }

    /**
     * Save password to history after successful change
     */
    private void savePasswordToHistory(Employee employee, String newPasswordHash, HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        EmployeePasswordHistory history = EmployeePasswordHistory.builder()
                .employeeId(employee.getId())
                .passwordHash(newPasswordHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        passwordHistoryRepository.save(history);

        // Clean up old entries (keep only last N + 1)
        int keepCount = passwordHistoryCount + 1;
        try {
            passwordHistoryRepository.deleteOldEntries(employee.getId(), keepCount);
        } catch (Exception e) {
            log.warn("Failed to clean up old password history entries: {}", e.getMessage());
        }
    }

    /**
     * Get client IP from request
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "Unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Validate tenant status
     */
    private void validateTenantStatus(Employee employee) {
        Tenant tenant = employee.getTenant();
        if (tenant == null) {
            return;
        }

        if (tenant.getStatus() == UserStatus.DELETED) {
            throw new BadCredentialsException("Tenant has been deleted. Please contact support.");
        }

        if (tenant.getStatus() == UserStatus.SUSPENDED) {
            throw new BadCredentialsException("Tenant is suspended. Please contact your administrator.");
        }

        if (!tenant.getIsActive()) {
            throw new BadCredentialsException("Tenant is inactive. Please contact your administrator.");
        }

        checkSubscriptionStatus(tenant);
    }

    /**
     * Check subscription status
     */
    private void checkSubscriptionStatus(Tenant tenant) {
        Optional<TenantSubscription> subscriptionOpt = subscriptionRepository
                .findByTenantIdAndIsCurrentTrue(tenant.getId());

        if (subscriptionOpt.isEmpty()) {
            return;
        }

        TenantSubscription subscription = subscriptionOpt.get();
        PlanStatus planStatus = subscription.getPlanStatus();

        if (planStatus == PlanStatus.ACTIVE ) {
            return;
        }

        if (planStatus == PlanStatus.PAST_DUE) {
            log.info("Tenant {} is in grace period (PAST_DUE)", tenant.getId());
            return;
        }

        if (planStatus == PlanStatus.EXPIRED) {
            TenantDataStatus dataStatus = tenant.getDataStatus();
            if (dataStatus == TenantDataStatus.RETAINED) {
                return;
            }
            throw new BadCredentialsException(
                    "Your subscription has expired. Please renew to continue accessing your account."
            );
        }

        if (planStatus == PlanStatus.CANCELLED) {
            if (subscription.getBillingPeriodEnd() != null &&
                    subscription.getBillingPeriodEnd().isBefore(LocalDateTime.now())) {
                throw new BadCredentialsException(
                        "Your subscription has been cancelled and the period has ended. Please renew."
                );
            }
            return;
        }

        if (planStatus == PlanStatus.TERMINATED || planStatus == PlanStatus.SUSPENDED) {
            throw new BadCredentialsException(
                    "Your subscription is " + planStatus.name().toLowerCase() + ". Please contact support."
            );
        }
    }
}