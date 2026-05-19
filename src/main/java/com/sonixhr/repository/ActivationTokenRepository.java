package com.sonixhr.repository;

import com.sonixhr.entity.ActivationToken;
import com.sonixhr.enums.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

    // Find valid token (not used, not expired)
    Optional<ActivationToken> findByTokenAndUsedFalseAndExpiryTimeAfter(String token, LocalDateTime now);

    // Find by token, user type, and validity
    Optional<ActivationToken> findByTokenAndUserTypeAndUsedFalseAndExpiryTimeAfter(
            String token, UserType userType, LocalDateTime now);

    // Find by user ID (all tokens for a user)
    Optional<ActivationToken> findByUserId(UUID userId);

    // Delete all tokens for a user (when resending activation)
    @Modifying
    @Transactional
    @Query("DELETE FROM ActivationToken t WHERE t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    // Delete expired tokens (cleanup job)
    @Modifying
    @Transactional
    @Query("DELETE FROM ActivationToken t WHERE t.expiryTime < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    // Mark token as used
    @Modifying
    @Transactional
    @Query("UPDATE ActivationToken t SET t.used = true WHERE t.token = :token")
    int markAsUsed(@Param("token") String token);

    // Check if user has any active token
    boolean existsByUserIdAndUsedFalseAndExpiryTimeAfter(UUID userId, LocalDateTime now);
}