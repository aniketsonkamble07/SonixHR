package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.TenantFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
 
public interface TenantFeatureRepository extends JpaRepository<TenantFeature, Long> {
    List<TenantFeature> findByTenantIdAndIsEnabledTrue(Long tenantId);
    Optional<TenantFeature> findByTenantIdAndFeatureName(Long tenantId, String featureName);
    boolean existsByTenantIdAndFeatureNameAndIsEnabledTrue(Long tenantId, String featureName);
}
