package com.sonixhr.service.platform;

import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handles all async email notifications for platform users.
 *
 * WHY THIS EXISTS:
 * Spring's @Async works via a proxy. When PlatformUserService called its own
 * protected @Async methods directly (self-invocation), it bypassed the proxy —
 * so the calls were synchronous and exceptions weren't isolated.
 *
 * By moving these methods into a separate @Service bean, all calls go through
 * Spring's proxy and @Async is actually honoured.
 *
 * All methods swallow exceptions after logging — a failed notification email
 * must never roll back the triggering business transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformNotificationService {

    private final EmailService emailService;

    @Async
    public void sendActivationEmail(String email, String fullName, String activationLink) {
        try {
            emailService.sendPlatformActivationEmail(email, fullName, activationLink);
            log.info("Activation email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send activation email to: {}", email, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String fullName, String resetLink) {
        try {
            emailService.sendPasswordResetEmail(email, fullName, resetLink);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
        }
    }

    @Async
    public void sendAccountActivatedNotification(String email, String fullName) {
        try {
            emailService.sendAccountActivatedNotification(email, fullName);
            log.info("Account activated notification sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send account activated notification to: {}", email, e);
        }
    }

    @Async
    public void sendAccountSuspendedNotification(String email, String fullName) {
        try {
            emailService.sendAccountSuspendedNotification(email, fullName);
            log.info("Account suspended notification sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send account suspended notification to: {}", email, e);
        }
    }

    @Async
    public void sendPasswordResetNotification(String email, String fullName) {
        try {
            emailService.sendPasswordResetNotification(email, fullName);
            log.info("Password reset notification sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset notification to: {}", email, e);
        }
    }
}