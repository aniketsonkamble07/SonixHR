package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.enums.PlatformUserStatus;
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
    // TENANT-SCOPED BASIC QUERIES (SHARED DATABASE)
    // =====================================================

    // Find by email within specific tenant
    Optional<PlatformUser> findByEmailAndTenantId(String email, Long tenantId);

    // Check existence by email within tenant
    boolean existsByEmailAndTenantId(String email, Long tenantId);

    // Find by ID with tenant validation
    Optional<PlatformUser> findByIdAndTenantId(Long id, Long tenantId);

    // Find all users for a tenant
    List<PlatformUser> findAllByTenantId(Long tenantId);

    Page<PlatformUser> findAllByTenantId(Long tenantId, Pageable pageable);

    // Find active users for a tenant
    List<PlatformUser> findByTenantIdAndIsActiveTrue(Long tenantId);

    Page<PlatformUser> findByTenantIdAndIsActiveTrue(Long tenantId, Pageable pageable);

    // =====================================================
    // LEGACY METHODS (DEPRECATED - Use tenant-scoped versions)
    // =====================================================

    @Deprecated
    Optional<PlatformUser> findByEmail(String email);

    @Deprecated
    boolean existsByEmail(String email);

    // =====================================================
    // FETCH WITH RELATIONS (TENANT-SCOPED)
    // =====================================================

    // Find by ID with roles (tenant-scoped)
    @Query("SELECT DISTINCT u FROM PlatformUser u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.tenantId = :tenantId")
    Optional<PlatformUser> findByIdWithRolesAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    // Find by ID with roles and permissions (tenant-scoped)
    @Query("SELECT DISTINCT u FROM PlatformUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId AND u.tenantId = :tenantId")
    Optional<PlatformUser> findByIdWithRolesAndPermissions(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // =====================================================
    // COUNT QUERIES (TENANT-SCOPED)
    // =====================================================

    long countByTenantIdAndIsActiveTrue(Long tenantId);
    long countByTenantIdAndIsActiveFalse(Long tenantId);
    long countByTenantIdAndStatus(Long tenantId, PlatformUserStatus status);

    // Count users by role ID within tenant
    @Query("SELECT COUNT(u) FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId AND u.tenantId = :tenantId")
    long countUsersByRoleIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    // Count users by role name within tenant
    @Query("SELECT COUNT(u) FROM PlatformUser u JOIN u.roles r WHERE r.name = :roleName AND u.tenantId = :tenantId")
    long countUsersByRoleNameAndTenantId(@Param("roleName") String roleName, @Param("tenantId") Long tenantId);

    // =====================================================
    // LEGACY COUNT METHODS (DEPRECATED)
    // =====================================================

    @Deprecated
    long countByIsActiveTrue();

    @Deprecated
    long countByIsActiveFalse();

    @Deprecated
    long countByStatus(PlatformUserStatus status);

    @Deprecated
    @Query("SELECT COUNT(u) FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    // =====================================================
    // PAGINATION (TENANT-SCOPED)
    // =====================================================

    Page<PlatformUser> findByTenantIdAndStatus(Long tenantId, PlatformUserStatus status, Pageable pageable);
    Page<PlatformUser> findByTenantIdAndIsActiveFalse(Long tenantId, Pageable pageable);

    // =====================================================
    // SEARCH QUERIES (TENANT-SCOPED)
    // =====================================================

    @Query("SELECT u FROM PlatformUser u WHERE u.tenantId = :tenantId AND " +
            "(LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<PlatformUser> searchUsersByTenant(@Param("tenantId") Long tenantId,
                                           @Param("searchTerm") String searchTerm,
                                           Pageable pageable);

    @Query("SELECT u FROM PlatformUser u WHERE u.tenantId = :tenantId AND " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<PlatformUser> searchUsersByTenantAndStatus(@Param("tenantId") Long tenantId,
                                                    @Param("searchTerm") String searchTerm,
                                                    @Param("status") PlatformUserStatus status,
                                                    Pageable pageable);

    // =====================================================
    // ROLE-BASED QUERIES (TENANT-SCOPED)
    // =====================================================

    // Find users by role ID within tenant
    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId AND u.tenantId = :tenantId")
    List<PlatformUser> findByRolesIdAndTenantId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    // Find users by role ID with pagination within tenant
    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId AND u.tenantId = :tenantId")
    Page<PlatformUser> findByRolesIdAndTenantId(@Param("roleId") Long roleId,
                                                @Param("tenantId") Long tenantId,
                                                Pageable pageable);

    // Find users by role name within tenant
    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.name = :roleName AND u.tenantId = :tenantId")
    List<PlatformUser> findByRoleNameAndTenantId(@Param("roleName") String roleName,
                                                 @Param("tenantId") Long tenantId);

    // Find users by multiple role IDs within tenant
    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id IN :roleIds AND u.tenantId = :tenantId")
    List<PlatformUser> findByAnyRoleIdAndTenantId(@Param("roleIds") List<Long> roleIds,
                                                  @Param("tenantId") Long tenantId);

    // Find users with no roles within tenant
    @Query("SELECT u FROM PlatformUser u WHERE u.tenantId = :tenantId AND u.roles IS EMPTY")
    List<PlatformUser> findUsersWithNoRoles(@Param("tenantId") Long tenantId);

    // =====================================================
    // LEGACY ROLE METHODS (DEPRECATED)
    // =====================================================

    @Deprecated
    @Query("SELECT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    List<PlatformUser> findByRolesId(@Param("roleId") Long roleId);

    @Deprecated
    @Query("SELECT u FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    Page<PlatformUser> findByRolesId(@Param("roleId") Long roleId, Pageable pageable);

    @Deprecated
    @Query("SELECT u FROM PlatformUser u JOIN u.roles r WHERE r.name = :roleName")
    List<PlatformUser> findByRoleName(@Param("roleName") String roleName);

    @Deprecated
    @Query("SELECT DISTINCT u FROM PlatformUser u JOIN u.roles r WHERE r.id IN :roleIds")
    List<PlatformUser> findByAnyRoleId(@Param("roleIds") List<Long> roleIds);

    // =====================================================
    // UPDATE QUERIES (TENANT-SCOPED)
    // =====================================================

    // Update last login with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.lastLoginAt = CURRENT_TIMESTAMP, u.lastLoginIp = :ipAddress WHERE u.id = :userId AND u.tenantId = :tenantId")
    int updateLastLogin(@Param("userId") Long userId,
                        @Param("ipAddress") String ipAddress,
                        @Param("tenantId") Long tenantId);

    // Update status with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status, u.updatedBy = :updatedBy WHERE u.id = :userId AND u.tenantId = :tenantId")
    int updateUserStatus(@Param("userId") Long userId,
                         @Param("status") PlatformUserStatus status,
                         @Param("updatedBy") Long updatedBy,
                         @Param("tenantId") Long tenantId);

    // Activate user with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.isActive = true, u.isEnabled = true, u.status = :status, u.updatedBy = :updatedBy WHERE u.id = :userId AND u.tenantId = :tenantId")
    int activateUser(@Param("userId") Long userId,
                     @Param("status") PlatformUserStatus status,
                     @Param("updatedBy") Long updatedBy,
                     @Param("tenantId") Long tenantId);

    // Deactivate user with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.isActive = false, u.isEnabled = false, u.status = :status, u.updatedBy = :updatedBy WHERE u.id = :userId AND u.tenantId = :tenantId")
    int deactivateUser(@Param("userId") Long userId,
                       @Param("status") PlatformUserStatus status,
                       @Param("updatedBy") Long updatedBy,
                       @Param("tenantId") Long tenantId);

    // Lock user account with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status, u.isAccountNonLocked = false, u.lockTime = :lockTime WHERE u.id = :userId AND u.tenantId = :tenantId")
    int lockUser(@Param("userId") Long userId,
                 @Param("status") PlatformUserStatus status,
                 @Param("lockTime") LocalDateTime lockTime,
                 @Param("tenantId") Long tenantId);

    // Unlock user account with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status, u.isAccountNonLocked = true, u.lockTime = NULL, u.failedLoginAttempts = 0 WHERE u.id = :userId AND u.tenantId = :tenantId")
    int unlockUser(@Param("userId") Long userId,
                   @Param("status") PlatformUserStatus status,
                   @Param("tenantId") Long tenantId);

    // Increment failed login attempts with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId AND u.tenantId = :tenantId")
    int incrementFailedLoginAttempts(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // Reset failed login attempts with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.failedLoginAttempts = 0, u.lockTime = NULL, u.isAccountNonLocked = true WHERE u.id = :userId AND u.tenantId = :tenantId")
    int resetFailedLoginAttempts(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // Update password with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.password = :password, u.passwordLastChanged = CURRENT_TIMESTAMP, u.mustChangePassword = false WHERE u.id = :userId AND u.tenantId = :tenantId")
    int updatePassword(@Param("userId") Long userId,
                       @Param("password") String password,
                       @Param("tenantId") Long tenantId);

    // Set password reset token with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.resetToken = :token, u.resetTokenExpiry = :expiry WHERE u.email = :email AND u.tenantId = :tenantId")
    int setPasswordResetToken(@Param("email") String email,
                              @Param("token") String token,
                              @Param("expiry") LocalDateTime expiry,
                              @Param("tenantId") Long tenantId);

    // Clear password reset token with tenant validation
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.resetToken = NULL, u.resetTokenExpiry = NULL WHERE u.id = :userId AND u.tenantId = :tenantId")
    int clearPasswordResetToken(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // =====================================================
    // EXISTENCE CHECKS (TENANT-SCOPED)
    // =====================================================

    boolean existsByEmailAndTenantIdAndIdNot(String email, Long tenantId, Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM PlatformUser u WHERE u.email = :email AND u.tenantId = :tenantId AND u.id != :id")
    boolean existsOtherUserWithEmailAndTenant(@Param("email") String email,
                                              @Param("tenantId") Long tenantId,
                                              @Param("id") Long id);

    // Check if user has specific role
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM PlatformUser u JOIN u.roles r WHERE u.id = :userId AND u.tenantId = :tenantId AND r.id = :roleId")
    boolean userHasRole(@Param("userId") Long userId,
                        @Param("tenantId") Long tenantId,
                        @Param("roleId") Long roleId);

    // =====================================================
    // DELETION (Soft Delete with Tenant Validation)
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.isActive = false, u.isEnabled = false, u.status = :status, u.deletedAt = CURRENT_TIMESTAMP, u.updatedBy = :updatedBy WHERE u.id = :userId AND u.tenantId = :tenantId")
    int softDeleteUser(@Param("userId") Long userId,
                       @Param("status") PlatformUserStatus status,
                       @Param("updatedBy") Long updatedBy,
                       @Param("tenantId") Long tenantId);

    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.isActive = true, u.isEnabled = true, u.status = :status, u.deletedAt = NULL, u.updatedBy = :updatedBy WHERE u.id = :userId AND u.tenantId = :tenantId")
    int restoreUser(@Param("userId") Long userId,
                    @Param("status") PlatformUserStatus status,
                    @Param("updatedBy") Long updatedBy,
                    @Param("tenantId") Long tenantId);

    // =====================================================
    // STATISTICS QUERIES (TENANT-SCOPED)
    // =====================================================

    @Query("SELECT u.status, COUNT(u) FROM PlatformUser u WHERE u.tenantId = :tenantId GROUP BY u.status")
    List<Object[]> countUsersByStatusAndTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT FUNCTION('DATE', u.createdAt), COUNT(u) FROM PlatformUser u WHERE u.tenantId = :tenantId AND u.createdAt >= :since GROUP BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> countNewUsersSinceAndTenant(@Param("tenantId") Long tenantId,
                                               @Param("since") LocalDateTime since);

    // Get user count by role for a tenant
    @Query("SELECT r.name, COUNT(u) FROM PlatformRole r LEFT JOIN r.users u WHERE r.tenantId = :tenantId GROUP BY r.id, r.name")
    List<Object[]> countUsersByRoleForTenant(@Param("tenantId") Long tenantId);

    // Get recently active users for a tenant
    @Query("SELECT u FROM PlatformUser u WHERE u.tenantId = :tenantId AND u.lastLoginAt >= :since ORDER BY u.lastLoginAt DESC")
    List<PlatformUser> findRecentlyActiveUsers(@Param("tenantId") Long tenantId,
                                               @Param("since") LocalDateTime since,
                                               Pageable pageable);

    // =====================================================
    // BULK OPERATIONS (TENANT-SCOPED)
    // =====================================================

    // Bulk update status for users
    @Modifying
    @Transactional
    @Query("UPDATE PlatformUser u SET u.status = :status, u.updatedBy = :updatedBy WHERE u.id IN :userIds AND u.tenantId = :tenantId")
    int bulkUpdateUserStatus(@Param("userIds") List<Long> userIds,
                             @Param("status") PlatformUserStatus status,
                             @Param("updatedBy") Long updatedBy,
                             @Param("tenantId") Long tenantId);

    // Bulk assign role to users
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO platform_user_roles (user_id, role_id) SELECT u.id, :roleId FROM platform_users u WHERE u.id IN :userIds AND u.tenant_id = :tenantId", nativeQuery = true)
    int bulkAssignRoleToUsers(@Param("userIds") List<Long> userIds,
                              @Param("roleId") Long roleId,
                              @Param("tenantId") Long tenantId);
}