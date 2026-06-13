package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<PlatformUser> findByEmail(String email);
    boolean existsByEmail(String email);

    // =====================================================
    // FETCH WITH RELATIONS (CRITICAL FOR AUTH)
    // =====================================================

    /**
     * Find user by email with roles eagerly fetched
     * This prevents N+1 queries during authentication
     */
    @Query("SELECT DISTINCT u FROM PlatformUser u LEFT JOIN FETCH u.roles r WHERE u.email = :email")
    Optional<PlatformUser> findByEmailWithRoles(@Param("email") String email);

    @Query("""
    SELECT DISTINCT u FROM PlatformUser u
    LEFT JOIN FETCH u.roles r
    LEFT JOIN FETCH r.permissions
    WHERE u.email = :email
    """)
    Optional<PlatformUser> findByEmailWithRolesAndPermissions(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM PlatformUser u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<PlatformUser> findByIdWithRoles(@Param("id") Long id);

    @Query("SELECT DISTINCT u FROM PlatformUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId")
    Optional<PlatformUser> findByIdWithRolesAndPermissions(@Param("userId") Long userId);

    // =====================================================
    // STATUS QUERIES
    // =====================================================

    List<PlatformUser> findByStatus(UserStatus status);
    Page<PlatformUser> findByStatus(UserStatus status, Pageable pageable);
    long countByStatus(UserStatus status);

    // =====================================================
    // SEARCH QUERIES
    // =====================================================

    @Query("SELECT u FROM PlatformUser u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<PlatformUser> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT u FROM PlatformUser u WHERE " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<PlatformUser> searchUsersByStatus(@Param("searchTerm") String searchTerm,
                                           @Param("status") UserStatus status,
                                           Pageable pageable);

    // =====================================================
    // ROLE-BASED QUERIES
    // =====================================================

    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    List<PlatformUser> findByRolesId(@Param("roleId") Long roleId);

    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    Page<PlatformUser> findByRolesId(@Param("roleId") Long roleId, Pageable pageable);

    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.name = :roleName")
    List<PlatformUser> findByRoleName(@Param("roleName") String roleName);

    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id IN :roleIds")
    List<PlatformUser> findByAnyRoleId(@Param("roleIds") List<Long> roleIds);

    @Query("SELECT u FROM PlatformUser u WHERE u.roles IS EMPTY")
    List<PlatformUser> findUsersWithNoRoles();

    @Query("SELECT COUNT(u) FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT COUNT(u) FROM PlatformUser u JOIN u.roles r WHERE r.name = :roleName")
    long countUsersByRoleName(@Param("roleName") String roleName);

    // =====================================================
    // UPDATE QUERIES
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status WHERE u.id = :userId")
    int updateUserStatus(@Param("userId") Long userId, @Param("status") UserStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.password = :password WHERE u.id = :userId")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM PlatformUser u WHERE u.email = :email AND u.id != :id")
    boolean existsOtherUserWithEmail(@Param("email") String email, @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM PlatformUser u JOIN u.roles r WHERE u.id = :userId AND r.id = :roleId")
    boolean userHasRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    // =====================================================
    // DELETION
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status WHERE u.id = :userId")
    int softDeleteUser(@Param("userId") Long userId, @Param("status") UserStatus status);

    // =====================================================
    // STATISTICS QUERIES
    // =====================================================

    @Query("SELECT u.status, COUNT(u) FROM PlatformUser u GROUP BY u.status")
    List<Object[]> countUsersByStatus();

    @Query("SELECT FUNCTION('DATE', u.createdAt), COUNT(u) FROM PlatformUser u WHERE u.createdAt >= :since GROUP BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> countNewUsersSince(@Param("since") LocalDateTime since);

    @Query("SELECT r.name, COUNT(u) FROM PlatformRole r LEFT JOIN r.users u GROUP BY r.id, r.name")
    List<Object[]> countUsersByRole();

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status WHERE u.id IN :userIds")
    int bulkUpdateUserStatus(@Param("userIds") List<Long> userIds, @Param("status") UserStatus status);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO platform_user_roles (user_id, role_id) VALUES (:userId, :roleId)", nativeQuery = true)
    int assignRoleToUser(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM platform_user_roles WHERE user_id = :userId AND role_id = :roleId", nativeQuery = true)
    int removeRoleFromUser(@Param("userId") Long userId, @Param("roleId") Long roleId);
}