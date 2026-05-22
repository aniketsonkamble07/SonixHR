package com.sonixhr.repository;

import com.sonixhr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // Fix: Use proper JPQL query since there's no direct tenantId field
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.email = :email")
    Optional<User> findByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.tenant.id = :tenantId AND u.email = :email")
    boolean existsByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId")
    Optional<User> findByIdWithRolesAndPermissions(@Param("userId") UUID userId);
}