package com.sonixhr.repository.department;

import com.sonixhr.entity.department.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Fix: Use tenant.id instead of tenantId
    Optional<Department> findByIdAndTenant_Id(Long id, UUID tenantId);

    List<Department> findByTenant_Id(UUID tenantId);

    Page<Department> findByTenant_Id(UUID tenantId, Pageable pageable);

    List<Department> findByTenant_IdAndIsActiveTrue(UUID tenantId);

    Optional<Department> findByTenant_IdAndCode(UUID tenantId, String code);

    Optional<Department> findByTenant_IdAndName(UUID tenantId, String name);

    boolean existsByTenant_IdAndName(UUID tenantId, String name);

    boolean existsByTenant_IdAndCode(UUID tenantId, String code);

    @Query("SELECT d FROM Department d WHERE d.tenant.id = :tenantId ORDER BY d.name ASC")
    List<Department> findAllByTenantIdOrderByName(@Param("tenantId") UUID tenantId);
}