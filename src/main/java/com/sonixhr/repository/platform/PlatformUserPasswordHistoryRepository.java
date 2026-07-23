package com.sonixhr.repository.platform;


import com.sonixhr.entity.platform.PlatformUserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformUserPasswordHistoryRepository extends JpaRepository<PlatformUserPasswordHistory, Long> {

    @Query(value = "SELECT ph.password_hash FROM platform_user_password_history ph " +
            "WHERE ph.user_id = :userId " +
            "ORDER BY ph.created_at DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<String> findLastNPasswordHashes(@Param("userId") Long userId, @Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM platform_user_password_history ph " +
            "WHERE ph.user_id = :userId " +
            "AND ph.id NOT IN (" +
            "   SELECT id FROM platform_user_password_history " +
            "   WHERE user_id = :userId " +
            "   ORDER BY created_at DESC " +
            "   LIMIT :keepCount" +
            ")", nativeQuery = true)
    void deleteOldEntries(@Param("userId") Long userId, @Param("keepCount") int keepCount);

    long countByUserId(Long userId);
}