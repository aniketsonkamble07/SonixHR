package com.sonixhr.repository.leave;

import com.sonixhr.entity.leave.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    // =====================================================
    // BASIC QUERIES
    // =====================================================

    Optional<PublicHoliday> findByTenantIdAndHolidayDate(Long tenantId, LocalDate date);

    List<PublicHoliday> findByTenantIdAndYear(Long tenantId, int year);

    List<PublicHoliday> findByTenantIdAndHolidayDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);

    boolean existsByTenantIdAndHolidayDate(Long tenantId, LocalDate date);

    // =====================================================
    // HOLIDAY TYPE QUERIES
    // =====================================================

    Optional<PublicHoliday> findByTenantIdAndHolidayDateAndType(Long tenantId, LocalDate date, String type);

    List<PublicHoliday> findByTenantIdAndType(Long tenantId, String type);

    List<PublicHoliday> findByTenantIdAndTypeIn(Long tenantId, List<String> types);

    // =====================================================
    // REGION/STATE SPECIFIC QUERIES
    // =====================================================

    Optional<PublicHoliday> findByTenantIdAndHolidayDateAndRegion(Long tenantId, LocalDate date, String region);

    List<PublicHoliday> findByTenantIdAndRegion(Long tenantId, String region);

    List<PublicHoliday> findByTenantIdAndRegionIn(Long tenantId, List<String> regions);

    // =====================================================
    // RECURRING HOLIDAYS
    // =====================================================

    List<PublicHoliday> findByTenantIdAndIsRecurringTrue(Long tenantId);

    @Query("SELECT h FROM PublicHoliday h WHERE h.tenantId = :tenantId " +
            "AND h.isRecurring = true " +
            "AND (h.year IS NULL OR h.year = :year)")
    List<PublicHoliday> findRecurringHolidaysForYear(@Param("tenantId") Long tenantId,
                                                     @Param("year") int year);

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Transactional
    @Modifying
    @Query("DELETE FROM PublicHoliday h WHERE h.tenantId = :tenantId AND h.year = :year")
    int deleteHolidaysForYear(@Param("tenantId") Long tenantId, @Param("year") int year);

    @Transactional
    @Modifying
    @Query("DELETE FROM PublicHoliday h WHERE h.tenantId = :tenantId AND h.holidayDate = :date")
    int deleteHolidayByDate(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    // =====================================================
    // UPCOMING HOLIDAYS
    // =====================================================

    @Query("SELECT h FROM PublicHoliday h WHERE h.tenantId = :tenantId " +
            "AND h.holidayDate >= CURRENT_DATE ORDER BY h.holidayDate ASC")
    List<PublicHoliday> findUpcomingHolidays(@Param("tenantId") Long tenantId);

    @Query("SELECT h FROM PublicHoliday h WHERE h.tenantId = :tenantId " +
            "AND h.holidayDate BETWEEN CURRENT_DATE AND CURRENT_DATE + :days")
    List<PublicHoliday> findUpcomingHolidaysWithinDays(@Param("tenantId") Long tenantId,
                                                       @Param("days") int days);
}