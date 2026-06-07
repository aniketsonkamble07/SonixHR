package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSetting;
import com.sonixhr.security.TenantAwareRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSettingRepository extends TenantAwareRepository<TenantSetting, Long> {
    List<TenantSetting> findByTenantId(Long tenantId);
    Optional<TenantSetting> findByTenantIdAndSettingKey(Long tenantId, String settingKey);
    void deleteByTenantIdAndSettingKey(Long tenantId, String settingKey);
}