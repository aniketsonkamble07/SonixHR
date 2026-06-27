package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformNotificationResponse;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/platform/notifications")
@RequiredArgsConstructor
public class PlatformNotificationController {

    private final PlatformNotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlatformNotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.info("REST request to get platform notifications for user: {}", currentUser.getId());
        List<PlatformNotificationResponse> notifications = notificationService.getMyNotifications(currentUser.getId());
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.info("REST request to mark platform notification {} as read", id);
        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}
