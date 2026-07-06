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

    private static final String REDIS_LIST_PREFIX  = "employee:unread_notifications:";
    private static final String REDIS_COUNT_PREFIX = "employee:unread_count:";   // O(1) counter
    private static final String REDIS_ID_COUNTER   = "global:notification:id";
    private static final long   TTL_DAYS           = 7;
    private static final long   MAX_ITEMS          = 50;

    /**
     * Create and store a notification. Increments the unread counter atomically.
     */
    public Notification sendNotification(@NonNull Employee recipient, @NonNull String title,
                                         @NonNull String message, @NonNull String type) {
        log.info("Creating notification for employee: {} - Title: {}", recipient.getId(), title);

        Long nextId = stringRedisTemplate.opsForValue().increment(REDIS_ID_COUNTER);
        if (nextId == null) {
            nextId = System.currentTimeMillis();
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

            // Store in Redis List (capped at MAX_ITEMS)
            String listKey = REDIS_LIST_PREFIX + recipient.getId();
            stringRedisTemplate.opsForList().leftPush(listKey, jsonPayload);
            stringRedisTemplate.opsForList().trim(listKey, 0, MAX_ITEMS - 1);
            stringRedisTemplate.expire(listKey, TTL_DAYS, TimeUnit.DAYS);

            // Increment the dedicated unread counter — O(1) atomic
            stringRedisTemplate.opsForValue().increment(REDIS_COUNT_PREFIX + recipient.getId());

            // Publish to Redis channel for real-time SSE delivery
            stringRedisTemplate.convertAndSend("employee:notifications:" + recipient.getId(), jsonPayload);
        } catch (Exception e) {
            log.error("Failed to store/publish notification to Redis", e);
        }

        return notification;
    }

    /**
     * Retrieve all notifications for the employee from Redis.
     */
    public List<Notification> getMyNotifications(@NonNull Long employeeId, @NonNull Long tenantId) {
        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Notification> list = new ArrayList<>();
        for (String json : rawList) {
            try {
                Notification n = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(n.getTenantId(), tenantId)) {
                    list.add(n);
                }
            } catch (Exception e) {
                log.error("Failed to deserialize notification from Redis", e);
            }
        }
        return list;
    }

    /**
     * O(1) unread count via dedicated Redis counter.
     * Falls back to list scan if counter is missing (e.g. after Redis restart).
     */
    public long getUnreadCount(@NonNull Long employeeId, @NonNull Long tenantId) {
        String raw = stringRedisTemplate.opsForValue().get(REDIS_COUNT_PREFIX + employeeId);
        if (raw != null) {
            try {
                return Math.max(0, Long.parseLong(raw));
            } catch (NumberFormatException e) {
                log.warn("Corrupt unread counter for employee {}, rebuilding", employeeId);
            }
        }

        // Counter missing — rebuild from list and repopulate
        long count = scanListForUnreadCount(employeeId, tenantId);
        stringRedisTemplate.opsForValue().set(REDIS_COUNT_PREFIX + employeeId, String.valueOf(count));
        return count;
    }

    /**
     * Mark a single notification as read. Decrements the counter only if it was unread.
     */
    public void markAsRead(@NonNull Long notificationId, @NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Marking notification {} as read for employee {}", notificationId, employeeId);

        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            // List gone (Redis restart without persistence) — treat as already read
            log.warn("Notification list missing for employee {} (Redis may have restarted). Notification {} treated as read.", employeeId, notificationId);
            stringRedisTemplate.opsForValue().set(REDIS_COUNT_PREFIX + employeeId, "0");
            return;
        }

        boolean found = false;
        boolean wasUnread = false;
        List<String> updatedList = new ArrayList<>();

        for (String json : rawList) {
            try {
                Notification n = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(n.getId(), notificationId)) {
                    if (!Objects.equals(n.getTenantId(), tenantId)) {
                        throw new BusinessException("Access denied: You cannot modify this notification");
                    }
                    wasUnread = Boolean.FALSE.equals(n.getIsRead());
                    n.setIsRead(true);
                    json = objectMapper.writeValueAsString(n);
                    found = true;
                }
                updatedList.add(json);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error updating notification in Redis", e);
            }
        }

        if (!found) {
            throw new ResourceNotFoundException("Notification not found");
        }

        rewriteList(listKey, updatedList);

        // Decrement counter only if it was actually unread
        if (wasUnread) {
            Long current = stringRedisTemplate.opsForValue().decrement(REDIS_COUNT_PREFIX + employeeId);
            if (current != null && current < 0) {
                stringRedisTemplate.opsForValue().set(REDIS_COUNT_PREFIX + employeeId, "0");
            }
        }
    }

    /**
     * Mark all notifications as read. Sets counter to 0 in one Redis SET.
     */
    public void markAllAsRead(@NonNull Long employeeId, @NonNull Long tenantId) {
        log.info("Marking all notifications as read for employee {}", employeeId);

        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(REDIS_COUNT_PREFIX + employeeId, "0");
            return;
        }

        List<String> updatedList = new ArrayList<>();
        for (String json : rawList) {
            try {
                Notification n = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(n.getTenantId(), tenantId)) {
                    n.setIsRead(true);
                    json = objectMapper.writeValueAsString(n);
                }
                updatedList.add(json);
            } catch (Exception e) {
                log.error("Error marking notification as read", e);
                updatedList.add(json);
            }
        }

        rewriteList(listKey, updatedList);
        // Single O(1) SET — no need to iterate
        stringRedisTemplate.opsForValue().set(REDIS_COUNT_PREFIX + employeeId, "0");
    }

    // -------------------------------------------------------------------------

    private long scanListForUnreadCount(Long employeeId, Long tenantId) {
        String listKey = REDIS_LIST_PREFIX + employeeId;
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        if (rawList == null || rawList.isEmpty()) return 0;
        long count = 0;
        for (String json : rawList) {
            try {
                Notification n = objectMapper.readValue(json, Notification.class);
                if (Objects.equals(n.getTenantId(), tenantId) && Boolean.FALSE.equals(n.getIsRead())) {
                    count++;
                }
            } catch (Exception ignored) { }
        }
        return count;
    }

    private void rewriteList(String listKey, List<String> items) {
        stringRedisTemplate.delete(listKey);
        if (!items.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(listKey, items);
            stringRedisTemplate.expire(listKey, TTL_DAYS, TimeUnit.DAYS);
        }
    }
}
