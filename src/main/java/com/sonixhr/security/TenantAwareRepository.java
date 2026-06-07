package com.sonixhr.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {

    // Use @Query with JPQL instead of method naming convention
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.tenant.id = :tenantId")
    Optional<T> findByIdAndTenantId(@Param("id") ID id, @Param("tenantId") Long tenantId);

    @Query("SELECT e FROM #{#entityName} e WHERE e.tenant.id = :tenantId")
    List<T> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT e FROM #{#entityName} e WHERE e.tenant.id = :tenantId")
    Page<T> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM #{#entityName} e WHERE e.id = :id AND e.tenant.id = :tenantId")
    boolean existsByIdAndTenantId(@Param("id") ID id, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);

    @Modifying
    @Query("DELETE FROM #{#entityName} e WHERE e.id = :id AND e.tenant.id = :tenantId")
    void deleteByIdAndTenantId(@Param("id") ID id, @Param("tenantId") Long tenantId);
}