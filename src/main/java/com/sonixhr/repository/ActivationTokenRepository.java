package com.sonixhr.repository;

import com.sonixhr.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    // =====================================================
    // FIND METHODS
    // =====================================================

    Optional<ActivationToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, LocalDateTime now);

    Optional<ActivationToken> findByToken(String token);

    Optional<ActivationToken> findByUserIdAndTokenTypeAndUsedFalseAndExpiresAtAfter(
            Long userId, String tokenType, LocalDateTime now);

    List<ActivationToken> findByUserIdAndTokenTypeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, String tokenType, LocalDateTime now);

    @Query("SELECT t FROM ActivationToken t WHERE t.expiresAt < :now AND t.used = false")
    List<ActivationToken> findExpiredTokens(@Param("now") LocalDateTime now);

    // =====================================================
    // DELETE METHODS
    // =====================================================

    @Modifying
    @Transactional
    @Query("DELETE FROM ActivationToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    void deleteByUserIdAndTokenType(Long userId, String tokenType);

    @Modifying
    @Transactional
    void deleteByUserIdAndTokenTypeAndUsedFalse(Long userId, String tokenType);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByToken(String token);

    // =====================================================
    // UPDATE METHODS
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE ActivationToken t SET t.used = true WHERE t.token = :token")
    int markAsUsed(@Param("token") String token);

    @Modifying
    @Transactional
    @Query("UPDATE ActivationToken t SET t.used = true WHERE t.userId = :userId AND t.tokenType = :tokenType AND t.used = false")
    int markAllUserTokensAsUsed(@Param("userId") Long userId, @Param("tokenType") String tokenType);

    @Modifying
    @Transactional
    @Query("UPDATE ActivationToken t SET t.expiresAt = :newExpiry WHERE t.token = :token AND t.used = false")
    int extendTokenExpiry(@Param("token") String token, @Param("newExpiry") LocalDateTime newExpiry);

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================



    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ActivationToken t " +
            "WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    boolean isValidToken(@Param("token") String token, @Param("now") LocalDateTime now);
}