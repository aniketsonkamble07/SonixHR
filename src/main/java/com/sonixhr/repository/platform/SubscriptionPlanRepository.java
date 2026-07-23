package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    @Query("SELECT p FROM SubscriptionPlan p WHERE LOWER(p.name) = LOWER(:name) AND p.deletedAt IS NULL")
    Optional<SubscriptionPlan> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL")
    List<SubscriptionPlan> findAllActivePlans();

    /**
     * Find all active plans sorted by price
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL AND p.isActive = true ORDER BY p.price ASC")
    List<SubscriptionPlan> findActivePlansOrderByPrice();

    /**
     * Find plan by code
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE LOWER(p.code) = LOWER(:code) AND p.deletedAt IS NULL")
    Optional<SubscriptionPlan> findByCodeIgnoreCase(@Param("code") String code);

    /**
     * Check if plan exists by name
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM SubscriptionPlan p WHERE LOWER(p.name) = LOWER(:name) AND p.deletedAt IS NULL")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    /**
     * Check if plan exists by code
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM SubscriptionPlan p WHERE LOWER(p.code) = LOWER(:code) AND p.deletedAt IS NULL")
    boolean existsByCodeIgnoreCase(@Param("code") String code);

    /**
     * Find all plans with pagination
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL ORDER BY p.displayOrder ASC, p.createdAt DESC")
    Page<SubscriptionPlan> findAllWithPagination(Pageable pageable);

    /**
     * Count total active plans
     */
    @Query("SELECT COUNT(p) FROM SubscriptionPlan p WHERE p.deletedAt IS NULL AND p.isActive = true")
    long countActivePlans();

    /**
     * Find public plans only
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL AND p.isActive = true AND p.isPublic = true ORDER BY p.displayOrder ASC, p.price ASC")
    List<SubscriptionPlan> findPublicPlans();

    /**
     * Find plans with max employees less than or equal to specified value
     */
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL AND p.isActive = true AND p.maxEmployees <= :maxEmployees ORDER BY p.price ASC")
    List<SubscriptionPlan> findPlansForEmployeeCount(@Param("maxEmployees") Integer maxEmployees);
}