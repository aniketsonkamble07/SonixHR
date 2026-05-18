package com.sonixhr.repository.tenant;

import com.sonixhr.entity.tenant.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySubdomain(String subdomain);
    Optional<Tenant> findByTenantCode(String tenantCode);
    Optional<Tenant> findByAdminEmail(String adminEmail);
    Page<Tenant> findByStatus(String status, Pageable pageable);
    boolean existsBySubdomain(String subdomain);
    boolean existsByTenantCode(String tenantCode);
}
