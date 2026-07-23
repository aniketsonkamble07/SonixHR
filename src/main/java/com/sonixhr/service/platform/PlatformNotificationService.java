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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformNotificationService {

    private final PlatformNotificationRepository notificationRepository;
    private final PlatformUserRepository userRepository;
    private final EmailService emailService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.notification.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.notification.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    @Value("${app.notification.max-results:100}")
    private int maxResults;

    private static final String REDIS_KEY_NOTIFICATIONS = "platform:notifications:user:";
    private static final String REDIS_KEY_UNREAD_COUNT = "platform:notifications:unread:";

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
    public void notifyPlatformTeam(String title, String message, String type, String ticketNumber,
                                   String companyName, String ticketStatus, String action) {
        log.info("Notifying platform team: {} - Action: {}", title, action);

        try {
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
                emailService.sendSupportTicketAlert(admin.getEmail(), admin.getFullName(),
                        ticketNumber, companyName, title, ticketStatus, action);
            }

            // Invalidate cache for all active admins
            for (PlatformUser admin : activeAdmins) {
                invalidateNotificationCache(admin.getId());
            }

            // Publish to Redis channel for live console alerts
            publishToRedis(title, message, type, ticketNumber);

        } catch (Exception e) {
            log.error("Failed to notify platform team", e);
            // Don't throw - we don't want to break the main flow
        }
    }

    /**
     * Retrieve notifications for a specific platform user with caching and pagination
     */
    @Transactional(readOnly = true, timeout = 3)
    public List<PlatformNotificationResponse> getMyNotifications(Long platformUserId) {
        log.info("Fetching platform notifications for user: {}", platformUserId);

        if (platformUserId == null) {
            log.warn("Attempted to get notifications with null user ID");
            return Collections.emptyList();
        }

        try {
            // Check cache first
            if (cacheEnabled) {
                List<PlatformNotificationResponse> cached = getCachedNotifications(platformUserId);
                if (cached != null) {
                    log.debug("Returning cached notifications for user: {}", platformUserId);
                    return cached;
                }
            }

            // Fetch from database with pagination to prevent large result sets
            Pageable pageable = PageRequest.of(0, maxResults);
            List<PlatformNotification> notifications =
                    notificationRepository.findMyNotifications(platformUserId, pageable);

            List<PlatformNotificationResponse> responses = notifications.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            // Cache the results
            if (cacheEnabled) {
                cacheNotifications(platformUserId, responses);
            }

            return responses;

        } catch (Exception e) {
            log.error("Failed to fetch notifications for user: {}", platformUserId, e);
            // Return empty list on error to prevent application failure
            return Collections.emptyList();
        }
    }

    /**
     * Mark a platform notification as read.
     */
    @Transactional
    public void markAsRead(Long id, Long platformUserId) {
        log.info("Marking platform notification {} as read for user {}", id, platformUserId);

        try {
            PlatformNotification notification = notificationRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

            // Security check
            if (notification.getPlatformUser() != null &&
                    !notification.getPlatformUser().getId().equals(platformUserId)) {
                throw new IllegalArgumentException("Access denied: You cannot modify this notification");
            }

            notification.setIsRead(true);
            notificationRepository.save(notification);

            // Invalidate cache for this user
            invalidateNotificationCache(platformUserId);

        } catch (ResourceNotFoundException e) {
            log.warn("Notification not found: {}", id);
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Access denied for notification: {}", id);
            throw e;
        } catch (Exception e) {
            log.error("Failed to mark notification as read: {}", id, e);
            throw new RuntimeException("Failed to update notification", e);
        }
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public void markAllAsRead(Long platformUserId) {
        log.info("Marking all notifications as read for user: {}", platformUserId);

        try {
            notificationRepository.markAllAsRead(platformUserId);
            invalidateNotificationCache(platformUserId);
            log.info("All notifications marked as read for user: {}", platformUserId);
        } catch (Exception e) {
            log.error("Failed to mark all notifications as read for user: {}", platformUserId, e);
            throw new RuntimeException("Failed to update notifications", e);
        }
    }

    /**
     * Get unread count for a user with caching
     */
    public long getUnreadCount(Long platformUserId) {
        if (platformUserId == null) {
            return 0;
        }

        try {
            // Check cache
            if (cacheEnabled) {
                String cacheKey = REDIS_KEY_UNREAD_COUNT + platformUserId;
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    try {
                        return Long.parseLong(cached);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid cached unread count: {}", cached);
                    }
                }
            }

            // Get from database
            long count = notificationRepository.countUnread(platformUserId);

            // Cache
            if (cacheEnabled) {
                String cacheKey = REDIS_KEY_UNREAD_COUNT + platformUserId;
                stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(count),
                        cacheTtlMinutes, TimeUnit.MINUTES);
            }

            return count;
        } catch (Exception e) {
            log.error("Failed to get unread count for user: {}", platformUserId, e);
            return 0;
        }
    }

    /**
     * Delete old notifications (scheduled cleanup)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Starting cleanup of old notifications");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(3); // Keep last 3 months
            List<PlatformUser> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

            int totalDeleted = 0;
            for (PlatformUser user : activeUsers) {
                // Keep only last 100 notifications per user
                notificationRepository.deleteOldNotifications(user.getId(), cutoffDate);
                totalDeleted++;
            }

            log.info("Cleaned up old notifications for {} users", totalDeleted);
        } catch (Exception e) {
            log.error("Failed to cleanup old notifications", e);
        }
    }

    // =====================================================
    // CACHE HELPERS
    // =====================================================

    @SuppressWarnings("unchecked")
    private List<PlatformNotificationResponse> getCachedNotifications(Long userId) {
        try {
            String cacheKey = REDIS_KEY_NOTIFICATIONS + userId;
            String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);

            if (cachedData != null) {
                return objectMapper.readValue(cachedData,
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                PlatformNotificationResponse.class));
            }
        } catch (Exception e) {
            log.debug("Failed to deserialize cached notifications: {}", e.getMessage());
        }
        return null;
    }

    private void cacheNotifications(Long userId, List<PlatformNotificationResponse> notifications) {
        try {
            if (!notifications.isEmpty()) {
                String cacheKey = REDIS_KEY_NOTIFICATIONS + userId;
                String jsonData = objectMapper.writeValueAsString(notifications);
                stringRedisTemplate.opsForValue().set(cacheKey, jsonData,
                        cacheTtlMinutes, TimeUnit.MINUTES);
                log.debug("Cached {} notifications for user: {}", notifications.size(), userId);
            }
        } catch (Exception e) {
            log.warn("Failed to cache notifications: {}", e.getMessage());
        }
    }

    private void invalidateNotificationCache(Long platformUserId) {
        if (cacheEnabled && platformUserId != null) {
            try {
                String cacheKey = REDIS_KEY_NOTIFICATIONS + platformUserId;
                String unreadKey = REDIS_KEY_UNREAD_COUNT + platformUserId;
                stringRedisTemplate.delete(cacheKey);
                stringRedisTemplate.delete(unreadKey);
                log.debug("Invalidated notification cache for user: {}", platformUserId);
            } catch (Exception e) {
                log.warn("Failed to invalidate notification cache: {}", e.getMessage());
            }
        }
    }

    private void publishToRedis(String title, String message, String type, String ticketNumber) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("message", message);
            payload.put("type", type);
            payload.put("ticketNumber", ticketNumber);
            payload.put("createdAt", LocalDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("platform:notifications", jsonPayload);
            log.info("Published platform notification to Redis channel platform:notifications");
        } catch (Exception e) {
            log.error("Failed to publish platform notification to Redis", e);
        }
    }

    // =====================================================
    // CONVERTERS
    // =====================================================

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

    // =====================================================
    // ADMIN METHODS
    // =====================================================

    /**
     * Invalidate cache for all users (for admin operations)
     */
    public void invalidateAllNotificationCaches() {
        if (cacheEnabled) {
            try {
                String pattern = REDIS_KEY_NOTIFICATIONS + "*";
                stringRedisTemplate.delete(stringRedisTemplate.keys(pattern));
                log.info("Invalidated all notification caches");
            } catch (Exception e) {
                log.warn("Failed to invalidate all notification caches: {}", e.getMessage());
            }
        }
    }

    /**
     * Create a notification for all active users (broadcast)
     */
    @Transactional
    public void broadcastNotification(String title, String message, String type) {
        log.info("Broadcasting notification: {}", title);

        try {
            List<PlatformUser> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

            for (PlatformUser user : activeUsers) {
                PlatformNotification notification = PlatformNotification.builder()
                        .platformUser(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .build();
                notificationRepository.save(notification);
            }

            // Invalidate all caches
            invalidateAllNotificationCaches();

            log.info("Broadcasted notification to {} users", activeUsers.size());
        } catch (Exception e) {
            log.error("Failed to broadcast notification", e);
            throw new RuntimeException("Failed to broadcast notification", e);
        }
    }

    /**
     * Get notification statistics
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            long totalUnread = 0;
            List<PlatformUser> users = userRepository.findByStatus(UserStatus.ACTIVE);
            for (PlatformUser user : users) {
                totalUnread += notificationRepository.countUnread(user.getId());
            }
            stats.put("totalUnread", totalUnread);
            stats.put("activeUsers", users.size());
            stats.put("cacheEnabled", cacheEnabled);
            stats.put("cacheTTLMinutes", cacheTtlMinutes);
            stats.put("maxResults", maxResults);
        } catch (Exception e) {
            log.error("Failed to get notification stats", e);
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}