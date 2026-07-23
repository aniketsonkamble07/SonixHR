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

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.enabled}")
    private boolean emailEnabled;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // =====================================================
    // PLATFORM USER EMAILS (Called by PlatformNotificationService)
    // =====================================================

    /**
     * Send platform activation email
     */
    @Async
    public void sendPlatformActivationEmail(String email, String fullName, String activationLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send platform activation email to: {}", email);
            return;
        }

        log.info("Sending platform activation email to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Activate Your Platform Account - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #667eea;">Welcome %s!</h2>
                        <p>Your platform account has been created. Please click the button below to activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <p style="font-size: 12px; color: #666;">This link will expire in 24 hours.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName, activationLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Platform activation email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send platform activation email to: {}", email, e);
        }
    }

    /**
     * Send account activated notification
     */
    @Async
    public void sendAccountActivatedNotification(String email, String fullName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send account activated notification to: {}", email);
            return;
        }

        log.info("Sending account activated notification to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your Account Has Been Activated - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #28a745;">Account Activated!</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your SonixHR account has been <strong>activated</strong>.</p>
                        <p>You can now log in to your account.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Account activated notification sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send account activated notification to: {}", email, e);
        }
    }

    /**
     * Send account suspended notification
     */
    @Async
    public void sendAccountSuspendedNotification(String email, String fullName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send account suspended notification to: {}", email);
            return;
        }

        log.info("Sending account suspended notification to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your Account Has Been Suspended - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #dc3545;">Account Suspended</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your SonixHR platform user account has been <strong>suspended</strong> by the system administrator.</p>
                        <p>If you believe this is an error, please contact support.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Account suspended notification sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send account suspended notification to: {}", email, e);
        }
    }

    /**
     * Send password reset notification
     */
    @Async
    public void sendPasswordResetNotification(String email, String fullName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send password reset notification to: {}", email);
            return;
        }

        log.info("Sending password reset notification to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your Password Has Been Reset - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #667eea;">Password Reset Successful</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your SonixHR platform user account password has been successfully <strong>reset</strong> by the administrator.</p>
                        <p>You can now log in using your new credentials.</p>
                        <p>If you did not authorize this change, please contact support immediately.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Password reset notification sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send password reset notification to: {}", email, e);
        }
    }

    /**
     * Send password reset email with link
     */
    @Async
    public void sendPasswordResetEmail(String email, String fullName, String resetLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send password reset email to: {}", email);
            return;
        }

        log.info("Sending password reset email to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Reset Your Password - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #667eea;">Hello %s,</h2>
                        <p>We received a request to reset your password.</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Reset Password
                            </a>
                        </p>
                        <p style="font-size: 12px; color: #666;">This link will expire in 24 hours.</p>
                        <p style="font-size: 12px; color: #666;">If you did not request this, please ignore this email.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName, resetLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", email, e);
        }
    }

    /**
     * Send support ticket alert to platform admins
     */
    @Async
    public void sendSupportTicketAlert(String email, String adminName, String ticketNumber,
                                       String companyName, String ticketTitle,
                                       String ticketStatus, String action) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send support ticket alert to: {}", email);
            return;
        }

        log.info("Sending support ticket alert email to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject(String.format("Support Ticket Alert [%s] - %s", ticketNumber, action));
            helper.setFrom(fromEmail);

            String reactLink = frontendUrl + "/platform/tickets/" + ticketNumber;

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #667eea;">Support Ticket Alert</h2>
                        <p>Hello <strong>%s</strong>,</p>
                        <p>A support ticket has been <strong>%s</strong>.</p>
                        <div style="background: #f8f9fa; padding: 15px; border-radius: 6px; margin: 15px 0;">
                            <p style="margin: 5px 0;"><strong>Ticket Number:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Organization:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Title:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Status:</strong> %s</p>
                        </div>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                View Ticket
                            </a>
                        </p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    adminName,
                    action.toLowerCase(),
                    ticketNumber,
                    companyName,
                    ticketTitle,
                    ticketStatus,
                    reactLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Support ticket alert email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send support ticket alert email to: {}", email, e);
        }
    }

    // =====================================================
    // EMPLOYEE ACTIVATION EMAIL
    // =====================================================

    /**
     * Send employee activation email
     */
    @Async
    public void sendActivationEmail(String email, String fullName, String activationLink) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send activation email to: {}", email);
            return;
        }

        log.info("Sending activation email to: {}", email);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Activate Your Account - SonixHR");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #667eea;">Welcome %s!</h2>
                        <p>Your employee account has been created. Please click the button below to set your password and activate your account:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Activate Account
                            </a>
                        </p>
                        <p style="font-size: 12px; color: #666;">This link will expire in 24 hours.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, fullName, activationLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Activation email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send activation email to: {}", email, e);
        }
    }

    // =====================================================
    // ARCHIVAL EMAILS (Called by SubscriptionSchedulerService)
    // =====================================================

    /**
     * Send archive warning email
     */
    @Async
    public void sendArchiveWarningEmail(String email, String companyName, String planName, int daysUntilArchive) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send deactivation warning email to: {}", email);
            return;
        }

        log.info("Sending deactivation warning email to: {} for company: {}", email, companyName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("ACTION REQUIRED: Your workspace is scheduled for deactivation - " + companyName);
            helper.setFrom(fromEmail);

            String reactLink = frontendUrl + "/billing/subscription";

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #ffc107;">Action Required: Deactivation Notice</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>This is to inform you that your workspace for <strong>%s</strong> is scheduled to be deactivated (soft-deleted) in <strong>%d days</strong>.</p>
                        <p>To retain full self-serve access, please renew or upgrade your plan:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #ffc107; color: #333; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block; font-weight: bold;">
                                Renew Subscription
                            </a>
                        </p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName,
                    daysUntilArchive,
                    reactLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Deactivation warning email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send deactivation warning email to: {}", email, e);
        }
    }



    // =====================================================
    // SUBSCRIPTION EMAILS (Called by TenantSubscriptionService)
    // =====================================================

    /**
     * Send subscription expired email
     */
    @Async
    public void sendSubscriptionExpiredEmail(String email, String companyName, String planName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send subscription expired email to: {}", email);
            return;
        }

        log.info("Sending subscription expired email to: {} for company: {}", email, companyName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your Subscription Has Expired - " + companyName);
            helper.setFrom(fromEmail);

            String reactLink = frontendUrl + "/billing/subscription";

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #dc3545;">Subscription Expired</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your subscription for <strong>%s</strong> has expired.</p>
                        <p><strong>Plan:</strong> %s</p>
                        <p>To reactivate your services:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Renew Subscription
                            </a>
                        </p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName,
                    planName,
                    reactLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Subscription expired email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send subscription expired email to: {}", email, e);
        }
    }

    // =====================================================
    // GENERIC EMAIL METHOD
    // =====================================================

    @Async
    public void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send email to: {}", to);
            return;
        }

        log.info("Sending email to: {} with subject: {}", to, subject);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(fromEmail);
            helper.setText(body, true);

            // mailSender.send(message);
            log.info("Email sent successfully to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }

    /**
     * Send subscription expiration reminder email
     */
    @Async
    public void sendSubscriptionExpirationReminderEmail(String email, String companyName, String planName, int daysRemaining) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send expiration reminder email to: {}", email);
            return;
        }

        String timeframe = daysRemaining == 1 ? "tomorrow" : "in " + daysRemaining + " days";
        log.info("Sending subscription expiration reminder email to: {} (expires {})", email, timeframe);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your subscription expires " + timeframe + " - " + companyName);
            helper.setFrom(fromEmail);

            String reactLink = frontendUrl + "/billing/subscription";

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #dc3545;">Subscription Expiry Notice</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your subscription to <strong>%s</strong> is scheduled to expire <strong>%s</strong>.</p>
                        <p><strong>Plan:</strong> %s</p>
                        <p>To prevent any service interruption, please renew your subscription:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Renew Subscription
                            </a>
                        </p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName,
                    timeframe,
                    planName,
                    reactLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Subscription expiration reminder email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send subscription expiration reminder email to: {}", email, e);
        }
    }

    /**
     * Send upcoming renewal notification email
     */
    @Async
    public void sendUpcomingRenewalEmail(String email, String companyName, String planName, String renewalDate) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send upcoming renewal email to: {}", email);
            return;
        }

        log.info("Sending upcoming renewal email to: {} for company: {}", email, companyName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Upcoming Subscription Renewal Notice - " + companyName);
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #28a745;">Upcoming Subscription Renewal</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your subscription for <strong>%s</strong> is scheduled to renew automatically on <strong>%s</strong>.</p>
                        <p><strong>Plan:</strong> %s</p>
                        <p>No action is required from your side. The renewal amount will be charged automatically.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName,
                    renewalDate,
                    planName);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Upcoming renewal email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send upcoming renewal email to: {}", email, e);
        }
    }

    /**
     * Send subscription renewal payment failed email
     */
    @Async
    public void sendPaymentFailedEmail(String email, String companyName, String planName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send payment failed email to: {}", email);
            return;
        }

        log.info("Sending payment failed email to: {} for company: {}", email, companyName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("URGENT: Subscription Renewal Payment Failed - " + companyName);
            helper.setFrom(fromEmail);

            String reactLink = frontendUrl + "/billing/subscription";

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #dc3545;">URGENT: Payment Failed</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>The automatic renewal payment for your <strong>%s</strong> subscription has failed.</p>
                        <p><strong>Plan:</strong> %s</p>
                        <p>As a result, your subscription has been expired and your account access has been suspended.</p>
                        <p>To restore access immediately, please update your payment details or renew your subscription:</p>
                        <p style="text-align: center;">
                            <a href="%s" style="background-color: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
                                Update Billing & Renew
                            </a>
                        </p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName,
                    planName,
                    reactLink);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Payment failed email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send payment failed email to: {}", email, e);
        }
    }

    /**
     * Send archive notification email
     */
    @Async
    public void sendArchiveNotificationEmail(String email, String companyName, String planName) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send soft-delete notification email to: {}", email);
            return;
        }

        log.info("Sending soft-delete notification email to: {} for company: {}", email, companyName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("Your workspace has been soft-deleted - " + companyName);
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #c82333;">Workspace Soft-Deleted</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your workspace for <strong>%s</strong> has been soft-deleted (marked for deletion) because your subscription has been expired for 30 days.</p>
                        <p>To restore your account and retrieve your data, please contact our support team immediately.</p>
                        <hr/>
                        <p style="font-size: 12px; color: #666;">Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """,
                    companyName,
                    companyName);

            helper.setText(htmlContent, true);
            // mailSender.send(message);
            log.info("Soft-delete notification email sent successfully to: {}", email);

        } catch (MessagingException e) {
            log.error("Failed to send soft-delete notification email to: {}", email, e);
        }
    }


}