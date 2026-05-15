package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, Long> {
    Optional<PlatformRole> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT r FROM PlatformRole r LEFT JOIN FETCH r.permissions WHERE r.id = :roleId")
    Optional<PlatformRole> findByIdWithPermissions(@Param("roleId") Long roleId);

    @Query("SELECT r FROM PlatformRole r LEFT JOIN FETCH r.permissions")
    List<PlatformRole> findAllWithPermissions();
}
