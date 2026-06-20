package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, Long> {

    @Query("SELECT p FROM PlatformNotification p WHERE p.platformUser.id = :platformUserId OR p.platformUser IS NULL ORDER BY p.createdAt DESC")
    List<PlatformNotification> findMyNotifications(@Param("platformUserId") Long platformUserId);

    @Query("SELECT COUNT(p) FROM PlatformNotification p WHERE (p.platformUser.id = :platformUserId OR p.platformUser IS NULL) AND p.isRead = false")
    long countUnread(@Param("platformUserId") Long platformUserId);
}
