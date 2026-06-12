package com.sonixhr.service.platform;

import com.sonixhr.dto.LoginResponse;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.security.JwtService;
import com.sonixhr.security.PlatformTokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// FIXES APPLIED:
//
// 1. login() now explicitly checks for SUSPENDED / DELETED users before issuing tokens.
//    Spring's AuthenticationManager only checks isEnabled(); a suspended user whose
//    isEnabled() returns true would otherwise get a token.
//
// 2. refresh() now verifies the user is still ACTIVE before issuing a new token pair.
//    Without this check a suspended user could keep refreshing indefinitely.
//
// 3. BadCredentialsException is re-thrown as-is (not wrapped). Wrapping it in a
//    RuntimeException changes the HTTP status from 401 to 500.
//
// 4. DisabledException and LockedException are caught in login() and re-thrown as
//    BadCredentialsException so @ControllerAdvice maps them to 401, not 500.

@Slf4j
@Service
public class PlatformAuthService {

    private final AuthenticationManager platformAuthenticationManager;
    private final PlatformUserRepository platformUserRepository;
    private final JwtService jwtService;
    private final PlatformTokenBlacklistService tokenBlacklistService;

    public PlatformAuthService(
            @Qualifier("platformAuthenticationManager") AuthenticationManager platformAuthenticationManager,
            PlatformUserRepository platformUserRepository,
            JwtService jwtService,
            PlatformTokenBlacklistService tokenBlacklistService) {
        this.platformAuthenticationManager = platformAuthenticationManager;
        this.platformUserRepository = platformUserRepository;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Authenticates the user, records last login, and returns a token pair.
     * @Transactional is here (not on the controller) — tight scope around DB work only.
     */
    @Transactional
    public LoginResponse login(String email, String password) {
        Authentication auth;
        try {
            auth = platformAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for: {}", email);
            throw e; // re-throw as-is → @ControllerAdvice → 401
        } catch (DisabledException | LockedException e) {
            // FIX 4: map account-state exceptions to 401, not 500
            log.warn("Login rejected for disabled/locked account: {}", email);
            throw new BadCredentialsException("Account is disabled or locked", e);
        }

        PlatformUser user = (PlatformUser) auth.getPrincipal();

        // FIX 1: explicit status guard — isEnabled() alone is not enough if
        // SUSPENDED users still return true from that method.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("Login attempt by suspended user: {}", email);
            throw new BadCredentialsException("Account is suspended");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("Login attempt by deleted user: {}", email);
            throw new BadCredentialsException("Account does not exist");
        }

        user.setLastLogin(LocalDateTime.now());
        platformUserRepository.save(user);

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        log.info("Platform user logged in: {}", email);

        return LoginResponse.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Validates a refresh token and issues a new token pair.
     * Throws BadCredentialsException on any failure → 401 via @ControllerAdvice.
     */
    public LoginResponse refresh(String refreshToken) {
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new BadCredentialsException("Token has been revoked");
        }

        if (!jwtService.validateToken(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(refreshToken);
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // FIX 2: re-check status on every refresh — suspended users must not get new tokens.
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Token refresh rejected for non-active user: {} (status={})", email, user.getStatus());
            throw new BadCredentialsException("Account is not active");
        }

        var tokenPair = jwtService.generatePlatformTokenPair(user);
        log.info("Token refreshed for: {}", email);

        return LoginResponse.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenPair.getExpiresIn())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }
}