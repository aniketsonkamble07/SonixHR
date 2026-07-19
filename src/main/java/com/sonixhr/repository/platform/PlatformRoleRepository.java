package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PlatformRoleRepository extends JpaRepository<PlatformRole, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    boolean existsByName(String name);
    Optional<PlatformRole> findByName(String name);

    @Query("SELECT DISTINCT r FROM PlatformRole r LEFT JOIN FETCH r.permissions")
    List<PlatformRole> findAllWithPermissions();

    // ✅ FIXED: Change from findByIsSystemRoleTrue to findBySystemRoleTrue
    List<PlatformRole> findBySystemRoleTrue();
    List<PlatformRole> findBySystemRoleFalse();

    // =====================================================
    // ADD THESE USEFUL METHODS
    // =====================================================
    List<PlatformRole> findByActiveTrue();
    List<PlatformRole> findByNameContainingIgnoreCase(String name);
    @Query("SELECT COUNT(u) > 0 FROM PlatformUser u JOIN u.roles r WHERE r.id = :roleId")
    boolean isRoleAssignedToUsers(@Param("roleId") Long roleId);
    List<PlatformRole> findAllByIdIn(Set<Long> ids);
    @Modifying
    @Transactional
    @Query("UPDATE PlatformRole r SET r.active = false WHERE r.id IN :roleIds")
    int bulkDeactivate(@Param("roleIds") Set<Long> roleIds);

    // ✅ FIXED: Change from countByIsSystemRoleTrue to countBySystemRoleTrue
    long countBySystemRoleTrue();
    long countBySystemRoleFalse();

    long countByActiveTrue();
    long countByActiveFalse();

    @Query("SELECT DISTINCT r FROM PlatformRole r LEFT JOIN FETCH r.permissions WHERE r.id = :roleId")
    Optional<PlatformRole> findByIdWithPermissions(@Param("roleId") Long roleId);
}