package com.sonixhr.repository.attendance;

import com.sonixhr.entity.attendance.ShiftConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftConfigurationRepository extends JpaRepository<ShiftConfiguration, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    List<ShiftConfiguration> findByTenantIdAndIsDeletedFalse(Long tenantId);

    Optional<ShiftConfiguration> findByTenantIdAndIsActiveTrue(Long tenantId);

    Optional<ShiftConfiguration> findByTenantIdAndIsDefaultTrueAndIsActiveTrue(Long tenantId);

    List<ShiftConfiguration> findAllByIsActiveTrue();

    List<ShiftConfiguration> findAllByTenantIdAndIsActiveTrue(Long tenantId);

    Optional<ShiftConfiguration> findByShiftCode(String shiftCode);

    List<ShiftConfiguration> findAllByTenantIdAndIsDeletedFalseOrderByEffectiveFromDesc(Long tenantId);

    // Used in service
    Optional<ShiftConfiguration> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    // Used in service
    Optional<ShiftConfiguration> findByShiftCodeAndTenantIdAndIsDeletedFalse(String shiftCode, Long tenantId);

    // =====================================================
    // FIND BY TENANT - ADDED MISSING METHODS
    // =====================================================

    List<ShiftConfiguration> findAllByTenantId(Long tenantId);

    Optional<ShiftConfiguration> findByTenantIdAndIsDefaultTrue(Long tenantId);

    Optional<ShiftConfiguration> findByTenantIdAndShiftCode(Long tenantId, String shiftCode);

    Optional<ShiftConfiguration> findByIdAndTenantId(Long id, Long tenantId);

    // =====================================================
    // EXISTENCE CHECKS
    // =====================================================

    boolean existsByTenantId(Long tenantId);

    boolean existsByTenantIdAndIsDeletedFalse(Long tenantId);

    boolean existsByShiftCode(String shiftCode);

    // Used in service for uniqueness check
    boolean existsByShiftCodeAndTenantIdAndIsDeletedFalse(String shiftCode, Long tenantId);

    // ADDED: Check exists by tenant and shift code
    boolean existsByTenantIdAndShiftCode(Long tenantId, String shiftCode);

    // =====================================================
    // DATE RANGE QUERIES
    // =====================================================

    @Query("SELECT s FROM ShiftConfiguration s WHERE s.tenantId = :tenantId " +
            "AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date) " +
            "AND s.isActive = true")
    Optional<ShiftConfiguration> findEffectiveOnDate(@Param("tenantId") Long tenantId,
                                                     @Param("date") LocalDate date);

    @Query("SELECT s FROM ShiftConfiguration s WHERE s.tenantId = :tenantId AND s.isDeleted = false " +
            "AND s.effectiveFrom <= :endDate AND (s.effectiveTo IS NULL OR s.effectiveTo >= :startDate)")
    List<ShiftConfiguration> findEffectiveBetween(@Param("tenantId") Long tenantId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // ADDED: Find effective shift by date and tenant
    @Query("SELECT s FROM ShiftConfiguration s WHERE s.tenantId = :tenantId " +
            "AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date) " +
            "AND s.isDeleted = false")
    Optional<ShiftConfiguration> findByTenantIdAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date);

    // =====================================================
    // TIME-BASED QUERIES
    // =====================================================

    @Query("SELECT s FROM ShiftConfiguration s WHERE s.tenantId = :tenantId " +
            "AND s.startTime <= :time AND s.endTime >= :time AND s.isActive = true")
    Optional<ShiftConfiguration> findByTimeRange(@Param("tenantId") Long tenantId,
                                                 @Param("time") LocalTime time);

    List<ShiftConfiguration> findByStartTimeBetween(LocalTime start, LocalTime end);

    // =====================================================
    // NIGHT SHIFT QUERIES
    // =====================================================

    @Query("SELECT s FROM ShiftConfiguration s WHERE s.endTime < s.startTime AND s.isActive = true")
    List<ShiftConfiguration> findAllNightShifts();

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM ShiftConfiguration s " +
            "WHERE s.id = :shiftId AND s.endTime < s.startTime")
    boolean isNightShift(@Param("shiftId") Long shiftId);

    // =====================================================
    // UPDATE QUERIES
    // =====================================================

    @Modifying
    @Transactional
    @Query("UPDATE ShiftConfiguration s SET s.isActive = false, s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.tenantId = :tenantId AND s.isActive = true")
    void deactivateAllShiftsForTenant(@Param("tenantId") Long tenantId);

    // Used in setDefaultShift
    @Modifying
    @Transactional
    @Query("UPDATE ShiftConfiguration s SET s.isDefault = false WHERE s.tenantId = :tenantId")
    void resetAllDefaultForTenant(@Param("tenantId") Long tenantId);

    @Modifying
    @Transactional
    @Query("UPDATE ShiftConfiguration s SET s.startTime = :startTime, s.endTime = :endTime, " +
            "s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :shiftId")
    void updateShiftTimings(@Param("shiftId") Long shiftId,
                            @Param("startTime") LocalTime startTime,
                            @Param("endTime") LocalTime endTime);

    // =====================================================
    // COUNT QUERIES
    // =====================================================

    long countByTenantIdAndIsActiveTrue(Long tenantId);

    long countByTenantIdAndIsDeletedFalse(Long tenantId);

    // ADDED: Count by tenant (without deleted filter)
    long countByTenantId(Long tenantId);

    // =====================================================
    // DEFAULT SHIFT QUERIES
    // =====================================================

    @Query("SELECT s FROM ShiftConfiguration s WHERE s.isDefault = true AND s.isActive = true")
    Optional<ShiftConfiguration> findDefaultShift();

    @Query("SELECT s FROM ShiftConfiguration s WHERE LOWER(s.shiftName) = LOWER(:shiftName) AND s.isActive = true")
    Optional<ShiftConfiguration> findByShiftNameIgnoreCase(@Param("shiftName") String shiftName);

    Optional<ShiftConfiguration> findTopByTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(Long tenantId);
}