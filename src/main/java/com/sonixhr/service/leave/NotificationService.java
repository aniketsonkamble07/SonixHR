package com.sonixhr.service.leave;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.Notification;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.repository.leave.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Create and save a notification for an employee.
     */
    @Transactional
    public Notification sendNotification(@NonNull Employee recipient, @NonNull String title, @NonNull String message, @NonNull String type) {
        log.info("Creating notification for employee: {} - Title: {}", recipient.getId(), title);

        Notification notification = Notification.builder()
                .tenant(recipient.getTenant())
                .employee(recipient)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", saved.getId());
            payload.put("title", saved.getTitle());
            payload.put("message", saved.getMessage());
            payload.put("type", saved.getType());
            payload.put("isRead", saved.getIsRead());
            payload.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("employee:notifications:" + recipient.getId(), jsonPayload);
            log.info("Published notification to Redis channel: employee:notifications:{}", recipient.getId());
        } catch (Exception e) {
            log.error("Failed to publish notification to Redis", e);
        }

        return saved;
    }

    /**
     * Retrieve all notifications for the employee.
     */
    public List<Notification> getMyNotifications(@NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Fetching notifications for employee: {} in tenant: {}", employeeId, tenantId);
        return notificationRepository.findByEmployeeIdAndTenantIdOrderByCreatedAtDesc(employeeId, tenantId);
    }

    /**
     * Mark a specific notification as read.
     */
    @Transactional
    public void markAsRead(@NonNull Long notificationId, @NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Marking notification {} as read for employee {}", notificationId, employeeId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getEmployee().getId().equals(employeeId) || !notification.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Access denied: You cannot modify this notification");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }
}
