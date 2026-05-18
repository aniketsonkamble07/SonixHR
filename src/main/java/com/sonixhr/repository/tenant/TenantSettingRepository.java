package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, Long> {
    List<TenantSetting> findByTenantId(UUID tenantId);
    Optional<TenantSetting> findByTenantIdAndSettingKey(UUID tenantId, String settingKey);
    void deleteByTenantIdAndSettingKey(UUID tenantId, String settingKey);
}