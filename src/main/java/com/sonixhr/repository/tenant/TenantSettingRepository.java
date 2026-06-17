package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSetting;
import com.sonixhr.security.TenantAwareRepository;
 
import java.util.List;
import java.util.Optional;

public interface TenantSettingRepository extends TenantAwareRepository<TenantSetting, Long> {
    List<TenantSetting> findByTenantId(Long tenantId);
    Optional<TenantSetting> findByTenantIdAndSettingKey(Long tenantId, String settingKey);
    void deleteByTenantIdAndSettingKey(Long tenantId, String settingKey);
}