package com.sonixhr.repository;

import com.sonixhr.entity.TenantSetupToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TenantSetupTokenRepository extends JpaRepository<TenantSetupToken, UUID> {
    Optional<TenantSetupToken> findByTokenAndUsedFalseAndExpiryAfter(String token, LocalDateTime now);

    @Modifying
    @Query("UPDATE TenantSetupToken t SET t.used = true WHERE t.token = :token")
    void markAsUsed(String token);
}
