package com.sonixhr.repository.platform;

import com.sonixhr.entity.platform.SubscriptionPlan;
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

    @Query("SELECT p FROM SubscriptionPlan p WHERE LOWER(p.code) = LOWER(:code) AND p.deletedAt IS NULL")
    Optional<SubscriptionPlan> findByCodeIgnoreCase(@Param("code") String code);

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.deletedAt IS NULL")
    List<SubscriptionPlan> findAllActivePlans();

    @Query("SELECT p FROM SubscriptionPlan p WHERE (LOWER(p.code) = LOWER(:codeOrName) OR LOWER(p.name) = LOWER(:codeOrName)) AND p.deletedAt IS NULL")
    Optional<SubscriptionPlan> findByCodeOrNameIgnoreCase(@Param("codeOrName") String codeOrName);
}
