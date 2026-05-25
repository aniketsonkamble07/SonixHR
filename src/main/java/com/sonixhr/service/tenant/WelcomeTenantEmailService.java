package com.sonixhr.service.tenant;

import org.springframework.mail.javamail.MimeMessageHelper;




import com.sonixhr.entity.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeTenantEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@sonixhr.com}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    /**
     * Send tenant welcome email after registration
     */
    public void sendTenantWelcomeEmail(String to, String adminName, String companyName,
                                       String subdomain, String activationToken,
                                       String planName, int trialDays) {
        String activationLink = baseUrl + "/api/public/activate?token=" + activationToken;
        String tenantUrl = "https://" + subdomain + ".sonixhr.com";
        String trialEndDate = LocalDate.now().plusDays(trialDays).format(DATE_FORMATTER);

        String subject = "Welcome to SonixHR - Activate Your Account";

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                    .content { background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                    .details { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                    .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 14px 35px; text-decoration: none; border-radius: 30px; font-weight: bold; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; }
                    .highlight { color: #667eea; font-weight: bold; }
                    .info-box { background: #e8f4fd; padding: 15px; border-radius: 8px; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1 style="margin: 0;">Welcome to SonixHR! 🎉</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Thank you for choosing SonixHR! Your account for <strong>%s</strong> has been created successfully.</p>
                        
                        <div class="details">
                            <h3 style="margin-top: 0; color: #667eea;">📋 Account Details</h3>
                            <p><strong>🏢 Company:</strong> %s</p>
                            <p><strong>🔗 Portal URL:</strong> <a href="%s" style="color: #667eea;">%s</a></p>
                            <p><strong>📊 Plan:</strong> <span class="highlight">%s</span></p>
                            <p><strong>⏰ Trial Period:</strong> %d days (ends on <strong>%s</strong>)</p>
                        </div>
                        
                        <p><strong>Ready to get started?</strong> Click the button below to activate your account and set up your password:</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">Activate My Account</a>
                        </div>
                        
                        <p style="font-size: 12px; color: #999; text-align: center;">This link will expire in <strong>24 hours</strong>.</p>
                        
                        <div class="info-box">
                            <strong>💡 Quick Tip:</strong> After activation, you'll have full access to:
                            <ul style="margin: 10px 0 0 20px;">
                                <li>Employee Management</li>
                                <li>Leave Tracking & Approvals</li>
                                <li>Attendance Management</li>
                                <li>HR Reports & Analytics</li>
                            </ul>
                        </div>
                        
                        <hr style="margin: 20px 0;"/>
                        
                        <p style="font-size: 12px; color: #666;">If you didn't sign up for SonixHR, please ignore this email.</p>
                        
                        <div class="footer">
                            <p>© 2026 SonixHR. All rights reserved.<br>
                            Need help? Contact us at <a href="mailto:support@sonixhr.com" style="color: #667eea;">support@sonixhr.com</a></p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, adminName, companyName, companyName, tenantUrl, tenantUrl,
                planName, trialDays, trialEndDate, activationLink);

        sendEmail(to, subject, htmlContent);
    }

    /**
     * Send trial reminder email
     */
    public void sendTrialReminderEmail(String to, String companyName, String message, LocalDateTime trialEndsAt) {
        String subject = "SonixHR - " + message;
        String trialEndDate = trialEndsAt.format(DATE_FORMATTER);

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                    .content { background: #fff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 8px; }
                    .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 30px; font-weight: bold; }
                    .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>⚠️ Trial Reminder</h2>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong> Team,</p>
                        
                        <div class="warning">
                            <p><strong>%s</strong></p>
                            <p>Your trial ends on <strong>%s</strong>.</p>
                        </div>
                        
                        <p>Don't lose access to your HR data! Upgrade now to continue using SonixHR.</p>
                        
                        <div style="text-align: center;">
                            <a href="https://app.sonixhr.com/billing" class="button">Upgrade Now</a>
                        </div>
                        
                        <div class="footer">
                            <p>Need help? Contact us at support@sonixhr.com</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, companyName, message, trialEndDate);

        sendEmail(to, subject, htmlContent);
    }

    /**
     * Send account activation confirmation email
     */
    public void sendActivationConfirmationEmail(String to, String name, String companyName) {
        String subject = "Welcome to SonixHR - Account Activated!";

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                    .content { background: #fff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                    .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 30px; font-weight: bold; }
                    .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Account Activated!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your account for <strong>%s</strong> has been successfully activated!</p>
                        
                        <p>You can now log in and start managing your HR operations:</p>
                        
                        <div style="text-align: center;">
                            <a href="https://app.sonixhr.com/login" class="button">Login to Your Account</a>
                        </div>
                        
                        <div class="footer">
                            <p>Thanks for choosing SonixHR!</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, name, companyName);

        sendEmail(to, subject, htmlContent);
    }

    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String to, String name, String resetToken) {
        String resetLink = baseUrl + "/api/auth/reset-password?token=" + resetToken;
        String subject = "SonixHR - Password Reset Request";

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                    .content { background: #fff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 8px; }
                    .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 30px; font-weight: bold; }
                    .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>🔐 Password Reset Request</h2>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        
                        <div class="warning">
                            <p>We received a request to reset your password.</p>
                        </div>
                        
                        <p>Click the button below to create a new password:</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">Reset Password</a>
                        </div>
                        
                        <p style="font-size: 12px; color: #999;">This link will expire in <strong>1 hour</strong>.</p>
                        
                        <hr/>
                        <p style="font-size: 12px; color: #666;">If you didn't request this, please ignore this email.</p>
                        
                        <div class="footer">
                            <p>SonixHR Support Team</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, name, resetLink);

        sendEmail(to, subject, htmlContent);
    }

    /**
     * Generic email sender
     */
    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(fromEmail);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}