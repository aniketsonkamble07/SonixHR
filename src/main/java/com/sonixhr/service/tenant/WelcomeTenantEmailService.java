package com.sonixhr.service.tenant;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WelcomeTenantEmailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeTenantEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@sonixhr.com}")
    private String fromEmail;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    // =====================================================
    // ✅ ADDED: Method called by TenantRegistrationService
    // =====================================================

    /**
     * Send welcome email to tenant admin (called after registration)
     */
    @Async
    public void sendWelcomeEmail(Tenant tenant, Employee superAdminEmployee) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send welcome email to: {}", tenant.getAdminEmail());
            return;
        }

        log.info("Sending welcome email to tenant admin: {} for company: {}",
                tenant.getAdminEmail(), tenant.getCompanyName());

        try {
            String tenantUrl = baseUrl;

            String subject = "Welcome to SonixHR - Activate Your Account";

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                        .content { background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                        .details { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                        .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 14px 35px; text-decoration: none; border-radius: 30px; font-weight: bold; margin: 20px 0; }
                        .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; }
                        .highlight { color: #667eea; font-weight: bold; }
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
                                <p><strong>🔗 Portal URL:</strong> <a href="%s">%s</a></p>
                                <p><strong>📊 Plan:</strong> <span class="highlight">%s</span></p>
                                <p><strong>⏰ Trial Period:</strong> %d days</p>
                            </div>
                            
                            <p>An activation email has been sent to <strong>%s</strong> with instructions to set up your password.</p>
                            
                            <div class="footer">
                                <p>© 2026 SonixHR. All rights reserved.<br>
                                Need help? Contact us at <a href="mailto:support@sonixhr.com">support@sonixhr.com</a></p>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """,
                    tenant.getAdminName(),
                    tenant.getCompanyName(),
                    tenant.getCompanyName(),
                    tenantUrl, tenantUrl,
                    tenant.getPlanType() != null ? tenant.getPlanType() : "Trial",
                    14,
                    tenant.getAdminEmail()
            );

            sendEmail(tenant.getAdminEmail(), subject, htmlContent);
            log.info("Welcome email sent successfully to: {}", tenant.getAdminEmail());

        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", tenant.getAdminEmail(), e);
            // ✅ Don't throw - email failure shouldn't break registration
        }
    }

    // =====================================================
    // TENANT WELCOME EMAIL (with activation token)
    // =====================================================

    @Async
    public void sendTenantWelcomeEmail(String to, String adminName, String companyName,
                                       String activationToken,
                                       String planName, int trialDays) {
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send tenant welcome email to: {}", to);
            return;
        }

        // ✅ FIXED: Correct activation endpoint
        String activationLink = baseUrl + "/api/tenant/employee/auth/activate?token=" + activationToken;
        String tenantUrl = baseUrl;
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
                        
                        <hr/>
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
     * Generic email sender - ✅ FIXED: Doesn't throw exception
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
            // ✅ FIXED: Don't throw - just log error
        }
    }
}