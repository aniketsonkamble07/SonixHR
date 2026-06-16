package com.sonixhr.controller.leave;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.Notification;
import com.sonixhr.service.leave.NotificationEmitterService;
import com.sonixhr.service.leave.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationEmitterService emitterService;

    /**
     * Establish a Server-Sent Events (SSE) streaming connection for real-time notifications on mobile/web.
     */
    @GetMapping("/stream")
    public SseEmitter streamNotifications(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to stream notifications for employee: {}", currentEmployee.getId());
        return emitterService.createEmitter(currentEmployee.getId());
    }

    /**
     * Retrieve all notifications for the authenticated employee (mobile app polling endpoint).
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications(
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get notifications for employee: {}", currentEmployee.getId());
        List<Notification> notifications = notificationService.getMyNotifications(
                currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok(notifications);
    }

    /**
     * Mark a specific notification as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to mark notification {} as read", id);
        notificationService.markAsRead(id, currentEmployee.getId(), currentEmployee.getTenantId());
        return ResponseEntity.ok().build();
    }
}
