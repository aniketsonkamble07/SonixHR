package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantSetting;
import com.sonixhr.security.TenantAwareRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSettingRepository extends TenantAwareRepository<TenantSetting, Long> {
    List<TenantSetting> findByTenantId(Long tenantId);
    Optional<TenantSetting> findByTenantIdAndSettingKey(Long tenantId, String settingKey);

    @Transactional
    void deleteByTenantIdAndSettingKey(Long tenantId, String settingKey);
}
