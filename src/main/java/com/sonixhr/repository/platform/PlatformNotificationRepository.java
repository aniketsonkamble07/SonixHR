package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, Long> {

    /**
     * Find notifications for a specific user or global notifications
     * with pagination support
     */
    @Query("SELECT p FROM PlatformNotification p " +
            "WHERE p.platformUser.id = :platformUserId OR p.platformUser IS NULL " +
            "ORDER BY p.createdAt DESC")
    List<PlatformNotification> findMyNotifications(
            @Param("platformUserId") Long platformUserId,
            Pageable pageable);

    /**
     * Find all notifications for a user (without pagination - use with caution)
     * Kept for backward compatibility but limited to prevent issues
     */
    @Query(value = "SELECT * FROM platform_notifications p " +
            "WHERE p.platform_user_id = :platformUserId OR p.platform_user_id IS NULL " +
            "ORDER BY p.created_at DESC LIMIT 100",
            nativeQuery = true)
    List<PlatformNotification> findMyNotifications(@Param("platformUserId") Long platformUserId);

    /**
     * Count unread notifications for a user
     */
    @Query("SELECT COUNT(p) FROM PlatformNotification p " +
            "WHERE (p.platformUser.id = :platformUserId OR p.platformUser IS NULL) " +
            "AND p.isRead = false")
    long countUnread(@Param("platformUserId") Long platformUserId);

    /**
     * Mark all notifications as read for a user
     */
    @Modifying
    @Query("UPDATE PlatformNotification p SET p.isRead = true " +
            "WHERE (p.platformUser.id = :platformUserId OR p.platformUser IS NULL) " +
            "AND p.isRead = false")
    void markAllAsRead(@Param("platformUserId") Long platformUserId);

    /**
     * Delete old notifications to keep the table small
     */
    @Modifying
    @Query("DELETE FROM PlatformNotification p " +
            "WHERE p.createdAt < :cutoffDate " +
            "AND (p.platformUser.id = :platformUserId OR p.platformUser IS NULL)")
    void deleteOldNotifications(@Param("platformUserId") Long platformUserId,
                                @Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Get only unread notifications (for performance)
     */
    @Query("SELECT p FROM PlatformNotification p " +
            "WHERE (p.platformUser.id = :platformUserId OR p.platformUser IS NULL) " +
            "AND p.isRead = false " +
            "ORDER BY p.createdAt DESC")
    List<PlatformNotification> findUnreadNotifications(
            @Param("platformUserId") Long platformUserId,
            Pageable pageable);
}