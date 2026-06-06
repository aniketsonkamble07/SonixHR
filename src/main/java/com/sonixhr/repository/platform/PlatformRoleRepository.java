package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRoleRepository extends JpaRepository<PlatformRole, Long> {

    // Tenant-scoped queries
    boolean existsByTenantIdAndName(Long tenantId, String name);
    Optional<PlatformRole> findByName(String name);
    Optional<PlatformRole> findByNameAndTenantId(String name, Long tenantId);

    Optional<PlatformRole> findByIdAndTenantId(Long id, Long tenantId);

    List<PlatformRole> findAllByTenantId(Long tenantId);

    @Query("SELECT DISTINCT r FROM PlatformRole r LEFT JOIN FETCH r.permissions WHERE r.tenantId = :tenantId")
    List<PlatformRole> findAllByTenantIdWithPermissions(@Param("tenantId") Long tenantId);



    List<PlatformRole> findByTenantIdAndIsSystemRoleTrue(Long tenantId);

    List<PlatformRole> findByTenantIdAndIsSystemRoleFalse(Long tenantId);

}