package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformUserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    // ===== Basic Queries =====
    Optional<PlatformUser> findByEmail(String email);
    boolean existsByEmail(String email);

    // ===== Count Queries =====
    long countByActiveTrue();
    long countByActiveFalse();
    long countByStatus(PlatformUserStatus status);

    // ===== Fetch with Relations =====
    @Query("SELECT u FROM PlatformUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId")
    Optional<PlatformUser> findByIdWithRolesAndPermissions(@Param("userId") UUID userId);

    // ===== Pagination =====
    Page<PlatformUser> findByStatus(PlatformUserStatus status, Pageable pageable);
    Page<PlatformUser> findByActiveTrue(Pageable pageable);

    // ===== Search =====
    @Query("SELECT u FROM PlatformUser u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<PlatformUser> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);
}