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

    // ✅ Basic queries (no tenantId)
    boolean existsByName(String name);
    Optional<PlatformRole> findByName(String name);

    @Query("SELECT DISTINCT r FROM PlatformRole r LEFT JOIN FETCH r.permissions")
    List<PlatformRole> findAllWithPermissions();

    List<PlatformRole> findByIsSystemRoleTrue();

    List<PlatformRole> findByIsSystemRoleFalse();
}