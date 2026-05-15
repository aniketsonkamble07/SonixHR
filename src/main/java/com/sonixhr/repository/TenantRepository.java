package com.sonixhr.repository;

import com.sonixhr.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCompanyName(String companyName);
    boolean existsBySubdomain(String subdomain);

    // Option 1: Naming convention (recommended)
    Optional<Tenant> findByTenantCode(String tenantCode);

    // Option 2: If you prefer custom @Query
    @Query("SELECT t FROM Tenant t WHERE t.tenantCode = :code")
    Optional<Tenant> findTenantByCode(@Param("code") String tenantCode);
}