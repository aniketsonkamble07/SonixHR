package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
    Optional<PlatformUser> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM PlatformUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId")
    Optional<PlatformUser> findByIdWithRolesAndPermissions(@Param("userId") UUID userId);
}