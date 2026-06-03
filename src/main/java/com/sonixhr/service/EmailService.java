/*package com.sonixhr.service;

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
}

 */
package com.sonixhr.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.email.from:noreply@sonixhr.com}")
    private String fromEmail;

    public void sendPlatformActivationEmail(String to, String name, String activationLink) {
        if (!emailEnabled) {
            log.info("📧 EMAIL DISABLED - Would send platform activation email to: {}", to);
            return;
        }

        log.info("📧 Sending platform activation email to: {}", to);

        try {
            Email from = new Email(fromEmail);
            Email toEmail = new Email(to);
            String subject = "Activate Your Platform Account - SonixHR";

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4F46E5;">Welcome %s!</h2>
                        <p>Your platform account has been created. Please click the link below to activate your account:</p>
                        <p><a href="%s" style="background-color: #4F46E5; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px;">Activate Account</a></p>
                        <p>Or copy and paste this link: <a href="%s">%s</a></p>
                        <p>This link will expire in 24 hours.</p>
                        <hr/>
                        <p>Thanks,<br/>SonixHR Team</p>
                    </div>
                </body>
                </html>
                """, name, activationLink, activationLink, activationLink);

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ Platform activation email sent to: {}", to);
            } else {
                log.error("❌ Failed to send email. Status code: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("SendGrid API returned error: " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("❌ Failed to send platform activation email to: {}", to, e);
            throw new RuntimeException("Failed to send activation email", e);
        }
    }

    public void sendWelcomeEmail(String to, String name) {
        if (!emailEnabled) {
            log.info("📧 EMAIL DISABLED - Would send welcome email to: {}", to);
            return;
        }

        log.info("📧 Sending welcome email to: {}", to);

        try {
            Email from = new Email(fromEmail);
            Email toEmail = new Email(to);
            String subject = "Welcome to SonixHR!";

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

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ Welcome email sent to: {}", to);
            } else {
                log.error("❌ Failed to send welcome email. Status code: {}", response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("❌ Failed to send welcome email to: {}", to, e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }

    public void sendEmployeeActivationEmail(String to, String name, String activationLink) {
        if (!emailEnabled) {
            log.info("📧 EMAIL DISABLED - Would send employee activation email to: {}", to);
            return;
        }

        log.info("📧 Sending employee activation email to: {}", to);

        try {
            Email from = new Email(fromEmail);
            Email toEmail = new Email(to);
            String subject = "Activate Your Employee Account - SonixHR";

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

            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ Employee activation email sent successfully to: {}", to);
            } else {
                log.error("❌ Failed to send employee activation email. Status code: {}", response.getStatusCode());
                throw new RuntimeException("SendGrid API returned error: " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("❌ Failed to send employee activation email to: {}", to, e);
            throw new RuntimeException("Failed to send employee activation email", e);
        }
    }
}