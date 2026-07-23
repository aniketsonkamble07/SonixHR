package com.sonixhr.service.platform;

import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.entity.platform.PlatformUserPasswordHistory;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.platform.PlatformUserPasswordHistoryRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.PlatformTokenBlacklistService;
import com.sonixhr.service.ActivationTokenService;
import com.sonixhr.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PlatformAuthService {

    private final AuthenticationManager platformAuthenticationManager;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformUserPasswordHistoryRepository passwordHistoryRepository;  // ✅ Add this
    private final JwtService jwtService;
    private final PlatformTokenBlacklistService tokenBlacklistService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final ActivationTokenService activationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final Pattern UPPERCASE_PAT = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PAT = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PAT = Pattern.compile("\\d");
    private static final Pattern SPECIAL_PAT = Pattern.compile("[@#$%^&+=!]");

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.password.history.enabled:true}")
    private boolean passwordHistoryEnabled;

    @Value("${app.password.history.count:5}")
    private int passwordHistoryCount;

    public PlatformAuthService(
            @Qualifier("platformAuthenticationManager") AuthenticationManager platformAuthenticationManager,
            PlatformUserRepository platformUserRepository,
            PlatformUserPasswordHistoryRepository passwordHistoryRepository,  // ✅ Add this
            JwtService jwtService,
            PlatformTokenBlacklistService tokenBlacklistService,
            PlatformUserDetailsService platformUserDetailsService,
            ActivationTokenService activationTokenService,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.platformAuthenticationManager = platformAuthenticationManager;
        this.platformUserRepository = platformUserRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;  // ✅ Add this
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.activationTokenService = activationTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Transactional
    public LoginResponse login(String email, String password) {
        Authentication auth;
        try {
            auth = platformAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for platform user: {}", email);
            throw e;
        } catch (DisabledException | LockedException e) {
            log.warn("Login rejected for disabled/locked platform account: {}", email);
            throw new BadCredentialsException("Account is disabled or locked", e);
        }

        PlatformUser user = (PlatformUser) auth.getPrincipal();

        // Explicit status guard
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("Login attempt by suspended platform user: {}", email);
            throw new BadCredentialsException("Account is suspended");
        }

        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("Login attempt by deleted platform user: {}", email);
            throw new BadCredentialsException("Account does not exist");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login attempt by inactive platform user: {} (status={})", email, user.getStatus());
            throw new BadCredentialsException("Account is not active");
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        PlatformUser savedUser = platformUserRepository.save(user);
        platformUserDetailsService.invalidateCache(savedUser.getEmail());

        // Generate token pair
        var tokenPair = jwtService.generatePlatformTokenPair(savedUser);
        log.info("Platform user logged in: {}", email);

        return LoginResponse.builder()
                .success(true)
                .message("Login successful")
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userId(user.getId())
                .requiresPasswordChange(false)
                .build();
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
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Re-check status on every refresh
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Token refresh rejected for non-active platform user: {} (status={})", email, user.getStatus());
            throw new BadCredentialsException("Account is not active");
        }

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        log.info("Token refreshed for platform user: {}", email);

        return LoginResponse.builder()
                .success(true)
                .message("Token refreshed successfully")
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userId(user.getId())
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
    // CHANGE PASSWORD (Authenticated) - With HttpServletRequest
    // =====================================================

    @Transactional
    public LoginResponse changePassword(PlatformUser currentUser, String currentPassword,
                                        String newPassword, String confirmPassword,
                                        HttpServletRequest request) {
        log.info("Changing password for platform user: {}", currentUser.getEmail());

        // 1. Verify current password
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
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
        if (passwordEncoder.matches(newPassword, currentUser.getPassword())) {
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
        currentUser.setPassword(newPasswordHash);
        currentUser.setPasswordLastChanged(LocalDateTime.now());
        currentUser.incrementRolesVersion();
        currentUser.clearAuthoritiesCache();

        platformUserRepository.save(currentUser);

        // 7. Save to password history
        savePasswordToHistory(currentUser, newPasswordHash, request);

        // 8. Invalidate cache
        platformUserDetailsService.invalidateCache(currentUser.getEmail());

        log.info("Password changed successfully for platform user: {}", currentUser.getEmail());

        return LoginResponse.builder()
                .success(true)
                .message("Password changed successfully. Please log in with your new password.")
                .build();
    }

    // =====================================================
    // CHANGE PASSWORD (Token-based - Backward Compatible)
    // =====================================================

    @Transactional
    public LoginResponse changePassword(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        log.info("Changing password for platform user using token");

        try {
            if (!newPassword.equals(confirmPassword)) {
                return LoginResponse.builder()
                        .success(false)
                        .message("Passwords do not match")
                        .errorCode("AUTH_005")
                        .build();
            }

            validatePasswordStrength(newPassword);

            String ipAddress = request != null ? request.getRemoteAddr() : "Unknown";
            String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

            activationTokenService.resetPasswordForPlatformUser(token, newPassword);

            log.info("Password changed successfully for platform user, IP: {}, User-Agent: {}", ipAddress, userAgent);

            return LoginResponse.builder()
                    .success(true)
                    .message("Password changed successfully. Please log in with your new password.")
                    .build();

        } catch (Exception e) {
            log.error("Failed to change password for platform user: {}", e.getMessage(), e);
            return LoginResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .errorCode("AUTH_006")
                    .build();
        }
    }

    // =====================================================
    // CHANGE PASSWORD (Without HttpServletRequest - Backward Compatible)
    // =====================================================

    @Transactional
    public LoginResponse changePassword(String token, String newPassword, String confirmPassword) {
        return changePassword(token, newPassword, confirmPassword, null);
    }

    // =====================================================
    // SET PASSWORD (Activation Flow) - With HttpServletRequest
    // =====================================================

    @Transactional
    public void setPassword(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        log.info("Setting password for platform user with activation token");

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);

        String ipAddress = request != null ? request.getRemoteAddr() : "Unknown";
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        activationTokenService.activatePlatformUser(token, newPassword, request);

        log.info("Password set successfully for platform user, IP: {}, User-Agent: {}", ipAddress, userAgent);
    }

    // =====================================================
    // SET PASSWORD (Without HttpServletRequest - Backward Compatible)
    // =====================================================

    @Transactional
    public void setPassword(String token, String newPassword, String confirmPassword) {
        setPassword(token, newPassword, confirmPassword, null);
    }

    // =====================================================
    // FORGOT PASSWORD
    // =====================================================

    @Transactional
    public void forgotPassword(String email) {
        log.info("Processing forgot password for platform user: {}", email);

        try {
            PlatformUser user = platformUserRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.info("Password reset requested for non-existent platform email: {}", email);
                return;
            }

            String resetToken = activationTokenService.generatePasswordResetTokenForPlatformUser(user.getId());
            String resetLink = baseUrl + "/api/platform/auth/reset-password?token=" + resetToken;

            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    resetLink
            );

            log.info("Password reset email sent to platform user: {}", email);

        } catch (Exception e) {
            log.error("Error processing forgot password for platform user {}: {}", email, e.getMessage());
        }
    }

    // =====================================================
    // RESET PASSWORD - With HttpServletRequest
    // =====================================================

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword, HttpServletRequest request) {
        log.info("Resetting password for platform user with token");

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        validatePasswordStrength(newPassword);

        String ipAddress = request != null ? request.getRemoteAddr() : "Unknown";
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        activationTokenService.resetPasswordForPlatformUser(token, newPassword);

        log.info("Password reset successfully for platform user, IP: {}, User-Agent: {}", ipAddress, userAgent);
    }

    // =====================================================
    // RESET PASSWORD (Without HttpServletRequest - Backward Compatible)
    // =====================================================

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        resetPassword(token, newPassword, confirmPassword, null);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        if (!UPPERCASE_PAT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }

        if (!LOWERCASE_PAT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }

        if (!DIGIT_PAT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }

        if (!SPECIAL_PAT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }

    /**
     * Validate that password was not used before (prevents password reuse)
     */
    private void validatePasswordNotUsedBefore(PlatformUser user, String newPassword) {
        // Skip for new accounts with no password
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return;
        }

        if (!passwordHistoryEnabled) {
            return;
        }

        // Get last N password hashes
        List<String> previousHashes = passwordHistoryRepository.findLastNPasswordHashes(
                user.getId(),
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
    private void savePasswordToHistory(PlatformUser user, String newPasswordHash, HttpServletRequest request) {
        String ipAddress = request != null ? request.getRemoteAddr() : "Unknown";
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        PlatformUserPasswordHistory history = PlatformUserPasswordHistory.builder()
                .userId(user.getId())
                .passwordHash(newPasswordHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        passwordHistoryRepository.save(history);

        // Clean up old entries (keep only last N + 1)
        int keepCount = passwordHistoryCount + 1;
        try {
            passwordHistoryRepository.deleteOldEntries(user.getId(), keepCount);
        } catch (Exception e) {
            log.warn("Failed to clean up old password history entries: {}", e.getMessage());
        }
    }
}