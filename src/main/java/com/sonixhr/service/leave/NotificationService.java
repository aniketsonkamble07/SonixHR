package com.sonixhr.service.leave;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.Notification;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_LIST_PREFIX = "employee:unread_notifications:";
    private static final String REDIS_ID_COUNTER = "global:notification:id";
    private static final long TTL_DAYS = 7;
    private static final long MAX_ITEMS = 50;

    /**
     * Create and save a notification for an employee.
     */
    public Notification sendNotification(@NonNull Employee recipient, @NonNull String title, @NonNull String message, @NonNull String type) {
        log.info("Creating notification for employee: {} - Title: {}", recipient.getId(), title);

        // Generate sequential Long ID using Redis atomic increment
        Long nextId = stringRedisTemplate.opsForValue().increment(REDIS_ID_COUNTER);
        if (nextId == null) {
            nextId = System.currentTimeMillis(); // Fallback ID
        }

        Notification notification = Notification.builder()
                .id(nextId)
                .tenantId(recipient.getTenantId())
                .employeeId(recipient.getId())
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        try {
            String jsonPayload = objectMapper.writeValueAsString(notification);

            // Store in Redis List
            String listKey = REDIS_LIST_PREFIX + recipient.getId();
            stringRedisTemplate.opsForList().leftPush(listKey, jsonPayload);
            stringRedisTemplate.opsForList().trim(listKey, 0, MAX_ITEMS - 1);
            stringRedisTemplate.expire(listKey, TTL_DAYS, TimeUnit.DAYS);

            // Publish to Redis channel for live console alerts
            stringRedisTemplate.convertAndSend("employee:notifications:" + recipient.getId(), jsonPayload);
            log.info("Published notification to Redis channel: employee:notifications:{}", recipient.getId());
        } catch (Exception e) {
            log.error("Failed to publish notification to Redis", e);
        }

        return notification;
    }

    /**
     * Retrieve all notifications for the employee.
     */
    public List<Notification> getMyNotifications(@NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Fetching notifications from Redis for employee: {} in tenant: {}", employeeId, tenantId);
        
        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Notification> list = new ArrayList<>();
        for (String json : rawList) {
            try {
                Notification notification = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(notification.getTenantId(), tenantId)) {
                    list.add(notification);
                }
            } catch (Exception e) {
                log.error("Failed to deserialize notification from Redis cache", e);
            }
        }
        return list;
    }

    /**
     * Mark a specific notification as read.
     */
    public void markAsRead(@NonNull Long notificationId, @NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Marking notification {} as read for employee {} in Redis", notificationId, employeeId);

        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            throw new ResourceNotFoundException("Notification not found");
        }

        boolean modified = false;
        List<String> updatedList = new ArrayList<>();
        
        for (String json : rawList) {
            try {
                Notification notification = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(notification.getId(), notificationId)) {
                    if (!Objects.equals(notification.getTenantId(), tenantId)) {
                        throw new BusinessException("Access denied: You cannot modify this notification");
                    }
                    notification.setIsRead(true);
                    json = objectMapper.writeValueAsString(notification);
                    modified = true;
                }
                updatedList.add(json);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error updating notification isRead status in Redis", e);
            }
        }

        if (!modified) {
            throw new ResourceNotFoundException("Notification not found");
        }

        // Rewrite the list back to Redis
        stringRedisTemplate.delete(listKey);
        if (!updatedList.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(listKey, updatedList);
            stringRedisTemplate.expire(listKey, TTL_DAYS, TimeUnit.DAYS);
        }
    }
}
// Force IDE cache refresh

