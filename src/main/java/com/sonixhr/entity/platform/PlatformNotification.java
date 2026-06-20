package com.sonixhr.entity.platform;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_notifications", indexes = {
        @Index(name = "idx_platform_notifications_user", columnList = "platform_user_id"),
        @Index(name = "idx_platform_notifications_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_user_id", nullable = true)
    private PlatformUser platformUser; // Nullable if broadcast to all admins

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 50)
    private String type; // e.g. "TICKET_CREATED", "TICKET_UPDATED"

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
