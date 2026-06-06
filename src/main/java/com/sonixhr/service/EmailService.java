package com.sonixhr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // =====================================================
    // PLATFORM USER EMAILS
    // =====================================================

    public void sendPlatformActivationEmail(String to, String name, String activationLink) {
        log.info("Sending platform activation email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Activate Your Platform Account - SonixHR");

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
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Platform activation email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send platform activation email to: {}", to, e);
            throw new RuntimeException("Failed to send activation email", e);
        }
    }

    public void sendWelcomeEmail(String to, String name) {
        log.info("Sending welcome email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Welcome to SonixHR!");

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
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Welcome email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to: {}", to, e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }

    /**
     * Send account activated notification (when admin activates a user)
     */
    public void sendAccountActivatedNotification(String to, String name) {
        log.info("Sending account activated notification to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Your Account Has Been Activated - SonixHR");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #10B981;">Account Activated!</h2>
                        <p>Dear %s,</p>
                        <p>Your SonixHR account has been <strong>activated</strong> by an administrator.</p>
                        <p>You can now:</p>
                        <ul>
                            <li>Log in to your account</li>
                            <li>Access all features based on your assigned roles</li>
                            <li>Update your profile and preferences</li>
                        </ul>
                        <p style="text-align: center;">
                            <a href="https://app.sonixhr.com/login" 
                               style="background-color: #10B981; color: white; padding: 12px 24px; 
                                      text-decoration: none; border-radius: 4px; display: inline-block;">
                                Log In Now
                            </a>
                        </p>
                        <p>If you have any questions, please contact your system administrator.</p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Account activated notification sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send account activated notification to: {}", to, e);
            // Don't throw exception - notification is non-critical
        }
    }

    /**
     * Send account deactivated notification (when admin deactivates a user)
     */
    public void sendAccountDeactivatedNotification(String to, String name) {
        log.info("Sending account deactivated notification to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Account Deactivated - SonixHR");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #EF4444;">Account Deactivated</h2>
                        <p>Dear %s,</p>
                        <p>Your SonixHR account has been <strong>deactivated</strong> by an administrator.</p>
                        <div style="background-color: #FEF2F2; border-left: 4px solid #EF4444; padding: 15px; margin: 20px 0;">
                            <p style="margin: 0; color: #991B1B;">
                                <strong>⚠️ What this means:</strong>
                            </p>
                            <ul style="margin: 10px 0 0 20px; color: #991B1B;">
                                <li>You cannot log in to your account</li>
                                <li>Your data remains intact but inaccessible</li>
                                <li>Contact your administrator to reactivate</li>
                            </ul>
                        </div>
                        <p>If you believe this was done in error, please contact your system administrator.</p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Thanks for your understanding,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Account deactivated notification sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send account deactivated notification to: {}", to, e);
            // Don't throw exception - notification is non-critical
        }
    }

    /**
     * Send account locked notification (due to too many failed attempts)
     */
    public void sendAccountLockedNotification(String to, String name, int minutesRemaining) {
        log.info("Sending account locked notification to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Account Locked - SonixHR");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #EF4444;">Account Temporarily Locked</h2>
                        <p>Dear %s,</p>
                        <p>Your account has been <strong>temporarily locked</strong> due to multiple failed login attempts.</p>
                        <div style="background-color: #FEF3C7; border-left: 4px solid #F59E0B; padding: 15px; margin: 20px 0;">
                            <p style="margin: 0;">
                                <strong>🔒 Lock Duration:</strong> %d minutes
                            </p>
                            <p style="margin: 10px 0 0 0;">
                                <strong>What you can do:</strong>
                            </p>
                            <ul style="margin: 10px 0 0 20px;">
                                <li>Wait %d minutes and try again</li>
                                <li>Use the "Forgot Password" option to reset your password</li>
                                <li>Contact support if you continue to have issues</li>
                            </ul>
                        </div>
                        <p style="text-align: center;">
                            <a href="https://app.sonixhr.com/forgot-password" 
                               style="background-color: #4F46E5; color: white; padding: 12px 24px; 
                                      text-decoration: none; border-radius: 4px; display: inline-block;">
                                Reset Password
                            </a>
                        </p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name, minutesRemaining, minutesRemaining);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Account locked notification sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send account locked notification to: {}", to, e);
        }
    }

    // =====================================================
    // EMPLOYEE EMAILS
    // =====================================================

    public void sendEmployeeActivationEmail(String to, String name, String activationLink) {
        log.info("Sending employee activation email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Activate Your Employee Account - SonixHR");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome to SonixHR, %s!</h2>
                        <p>Your employee account has been created by your organization.</p>
                        <p>Please click the button below to set your password and activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <p>Or copy and paste this link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        <p><strong>This link will expire in 24 hours.</strong></p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            If you didn't request this, please ignore this email.<br/>
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name, activationLink, activationLink, activationLink);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Employee activation email sent successfully to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send employee activation email to: {}", to, e);
            throw new RuntimeException("Failed to send employee activation email", e);
        }
    }

    // =====================================================
    // PASSWORD RESET EMAILS
    // =====================================================

    /**
     * Send password reset email with reset link
     */
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        log.info("Sending password reset email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Reset Your Password - SonixHR");

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
                        <p>Or copy and paste this link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        <p><strong>This link will expire in 24 hours.</strong></p>
                        <p>If you did not request this, please ignore this email and your password will remain unchanged.</p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name, resetLink, resetLink, resetLink);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", to, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Send password reset confirmation notification (after password is changed)
     */
    public void sendPasswordResetNotification(String to, String name) {
        log.info("Sending password reset notification to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Password Changed Successfully - SonixHR");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Hello %s,</h2>
                        <p>Your password has been successfully changed.</p>
                        <p>If you made this change, you can safely ignore this email.</p>
                        <div style="background-color: #FEF3C7; border-left: 4px solid #F59E0B; padding: 15px; margin: 20px 0;">
                            <p style="margin: 0; color: #92400E;">
                                <strong>⚠️ Security Alert:</strong> If you did not change your password, please contact support immediately.
                            </p>
                        </div>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Password reset notification sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send password reset notification to: {}", to, e);
            // Don't throw exception for notification email failure
        }
    }

    // =====================================================
    // TENANT REGISTRATION EMAIL
    // =====================================================

    /**
     * Send tenant welcome email after registration
     */
    public void sendTenantWelcomeEmail(String to, String name, String companyName,
                                       String subdomain, String activationLink,
                                       String planType, int trialDays) {
        log.info("Sending tenant welcome email to: {}", to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Welcome to SonixHR - Your Account is Ready!");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Thank you for choosing SonixHR for <strong>%s</strong>.</p>
                        <p>Your account has been created with the <strong>%s</strong> plan.</p>
                        <h3>Getting Started:</h3>
                        <ul>
                            <li>Complete your profile setup</li>
                            <li>Add your employees</li>
                            <li>Configure attendance policies</li>
                            <li>Set up shift schedules</li>
                        </ul>
                        <p>Click the button below to activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <p><strong>Trial Period: %d days</strong></p>
                        <p><strong>Your subdomain:</strong> %s.sonixhr.com</p>
                        <hr/>
                        <p style="color: #666; font-size: 12px;">
                            Need help? Contact our support team.<br/>
                            Thanks,<br/>
                            SonixHR Team
                        </p>
                    </div>
                </body>
                </html>
                """, name, companyName, planType, activationLink, trialDays, subdomain);

            helper.setText(htmlContent, true);
            helper.setFrom("noreply@sonixhr.com");

            mailSender.send(message);
            log.info("Tenant welcome email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send tenant welcome email to: {}", to, e);
            throw new RuntimeException("Failed to send tenant welcome email", e);
        }
    }
}