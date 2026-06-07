package com.sonixhr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@sonixhr.com}")
    private String fromEmail;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    // =====================================================
    // EMPLOYEE ACTIVATION EMAIL - FIXED
    // =====================================================

    /**
     * ✅ ADDED: Send employee activation email (called by TenantRegistrationService)
     */
    @Async
    public void sendActivationEmail(String to, String name, String activationLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send activation email to: {}", to);
            return;
        }

        log.info("Sending activation email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Activate Your Account - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Your account has been created. Please click the button below to set your password and activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <p>Or copy and paste this link: <a href="%s">%s</a></p>
                        <p><strong>This link will expire in 24 hours.</strong></p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name, activationLink, activationLink, activationLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Activation email sent successfully to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send activation email to: {}", to, e);
            // ✅ FIXED: Don't throw exception - email failure shouldn't break registration
        }
    }

    // =====================================================
    // PLATFORM USER EMAILS
    // =====================================================

    @Async
    public void sendPlatformActivationEmail(String to, String name, String activationLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send platform activation email to: {}", to);
            return;
        }

        log.info("Sending platform activation email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Activate Your Platform Account - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Your platform account has been created. Please click the link below to activate your account:</p>
                        <p><a href="%s">%s</a></p>
                        <p>This link will expire in 24 hours.</p>
                        <hr/>
                        <p>Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name, activationLink, activationLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Platform activation email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send platform activation email to: {}", to, e);
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send welcome email to: {}", to);
            return;
        }

        log.info("Sending welcome email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Welcome to SonixHR!");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Your account has been successfully activated.</p>
                        <p>You can now log in to your account.</p>
                        <hr/>
                        <p>Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Welcome email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to: {}", to, e);
        }
    }

    // =====================================================
    // PASSWORD RESET EMAILS
    // =====================================================

    @Async
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send password reset email to: {}", to);
            return;
        }

        log.info("Sending password reset email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Reset Your Password - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Hello %s,</h2>
                        <p>We received a request to reset your password.</p>
                        <p>Click the button below to create a new password:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Reset Password
                            </a>
                        </p>
                        <p>This link will expire in 24 hours.</p>
                        <p>If you did not request this, please ignore this email.</p>
                        <hr/>
                        <p>Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name, resetLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", to, e);
        }
    }

    // =====================================================
    // TENANT REGISTRATION EMAIL
    // =====================================================

    @Async
    public void sendTenantWelcomeEmail(String to, String name, String companyName,
                                       String subdomain, String activationLink,
                                       String planType, int trialDays) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send tenant welcome email to: {}", to);
            return;
        }

        log.info("Sending tenant welcome email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Welcome to SonixHR - Your Account is Ready!");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Thank you for choosing SonixHR for <strong>%s</strong>.</p>
                        <p>Your account has been created with the <strong>%s</strong> plan.</p>
                        <p><strong>Trial Period: %d days</strong></p>
                        <p><strong>Your subdomain:</strong> %s.sonixhr.com</p>
                        <p>Click the button below to activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name, companyName, planType, trialDays, subdomain, activationLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Tenant welcome email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send tenant welcome email to: {}", to, e);
        }
    }

    // =====================================================
    // NOTIFICATION EMAILS
    // =====================================================

    @Async
    public void sendAccountActivatedNotification(String to, String name) {
        if (!emailEnabled) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Your Account Has Been Activated - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #10B981;">Account Activated!</h2>
                        <p>Dear %s,</p>
                        <p>Your SonixHR account has been <strong>activated</strong>.</p>
                        <p>You can now log in to your account.</p>
                        <hr/>
                        <p>Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Account activated notification sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send account activated notification to: {}", to, e);
        }
    }
}