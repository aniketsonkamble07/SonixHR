package com.sonixhr.service.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.platform.PlatformNotificationResponse;
import com.sonixhr.entity.platform.PlatformNotification;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformNotificationRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles all email and in-app notifications for platform users.
 *
 * WHY THIS EXISTS:
 * Spring's @Async works via a proxy. When PlatformUserService called its own
 * protected @Async methods directly (self-invocation), it bypassed the proxy —
 * so the calls were synchronous and exceptions weren't isolated.
 *
 * By moving these methods into a separate @Service bean, all calls go through
 * Spring's proxy and @Async is actually honoured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class PlatformNotificationService {

    private final PlatformNotificationRepository notificationRepository;
    private final PlatformUserRepository userRepository;
    private final EmailService emailService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // =====================================================
    // ASYNC EMAIL NOTIFICATIONS
    // =====================================================

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

    // =====================================================
    // IN-APP & REAL-TIME PLATFORM TEAM NOTIFICATIONS
    // =====================================================

    @Transactional
    public void notifyPlatformTeam(String title, String message, String type, String ticketNumber, String companyName, String ticketStatus, String action) {
        log.info("Notifying platform team: {} - Action: {}", title, action);

        List<PlatformUser> activeAdmins = userRepository.findByStatus(UserStatus.ACTIVE);

        for (PlatformUser admin : activeAdmins) {
            // Persist notification for the admin
            PlatformNotification notification = PlatformNotification.builder()
                    .platformUser(admin)
                    .title(title)
                    .message(message)
                    .type(type)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);

            // Send email asynchronously
            emailService.sendSupportTicketAlert(admin.getEmail(), admin.getFullName(), ticketNumber, companyName, title, ticketStatus, action);
        }

        // Publish to Redis channel for live console alerts
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("message", message);
            payload.put("type", type);
            payload.put("ticketNumber", ticketNumber);
            payload.put("createdAt", java.time.LocalDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("platform:notifications", jsonPayload);
            log.info("Published platform notification to Redis channel platform:notifications");
        } catch (Exception e) {
            log.error("Failed to publish platform notification to Redis", e);
        }
    }

    /**
     * Retrieve notifications for a specific platform user.
     */
    public List<PlatformNotificationResponse> getMyNotifications(Long platformUserId) {
        log.info("Fetching platform notifications for user: {}", platformUserId);
        return notificationRepository.findMyNotifications(platformUserId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Mark a platform notification as read.
     */
    @Transactional
    public void markAsRead(Long id, Long platformUserId) {
        log.info("Marking platform notification {} as read for user {}", id, platformUserId);
        PlatformNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (notification.getPlatformUser() != null && !notification.getPlatformUser().getId().equals(platformUserId)) {
            throw new IllegalArgumentException("Access denied: You cannot modify this notification");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    private PlatformNotificationResponse toResponse(PlatformNotification notification) {
        return PlatformNotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}