package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.enums.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformPermissionRepository extends JpaRepository<PlatformPermission, Long> {
    Optional<PlatformPermission> findByName(AdminPermission name);
    boolean existsByName(AdminPermission name);
}