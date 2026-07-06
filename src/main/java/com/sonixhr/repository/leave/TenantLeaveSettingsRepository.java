package com.sonixhr.repository.leave;

import com.sonixhr.entity.leave.TenantLeaveSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TenantLeaveSettingsRepository extends JpaRepository<TenantLeaveSettings, Long> {

    Optional<TenantLeaveSettings> findByTenantId(Long tenantId);

    boolean existsByTenantId(Long tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE TenantLeaveSettings t SET t.weekendConfig = :weekendConfig, " +
            "t.customWeekendDays = :customWeekendDays, t.countWeekendsAsLeave = :countWeekendsAsLeave, " +
            "t.countHolidaysAsLeave = :countHolidaysAsLeave WHERE t.tenantId = :tenantId")
    int updateWeekendSettings(@Param("tenantId") Long tenantId,
                              @Param("weekendConfig") String weekendConfig,
                              @Param("customWeekendDays") String customWeekendDays,
                              @Param("countWeekendsAsLeave") Boolean countWeekendsAsLeave,
                              @Param("countHolidaysAsLeave") Boolean countHolidaysAsLeave);

    @Transactional
    @Modifying
    @Query("UPDATE TenantLeaveSettings t SET t.casualLeavePerYear = :casual, " +
            "t.sickLeavePerYear = :sick, t.earnedLeavePerYear = :earned, " +
            "t.emergencyLeavePerYear = :emergency, t.maternityLeavePerYear = :maternity, " +
            "t.paternityLeavePerYear = :paternity, t.unpaidLeavePerYear = :unpaid, " +
            "t.compensatoryLeavePerYear = :compensatory " +
            "WHERE t.tenantId = :tenantId")
    int updateLeaveBalances(@Param("tenantId") Long tenantId,
                            @Param("casual") Integer casual,
                            @Param("sick") Integer sick,
                            @Param("earned") Integer earned,
                            @Param("emergency") Integer emergency,
                            @Param("maternity") Integer maternity,
                            @Param("paternity") Integer paternity,
                            @Param("unpaid") Integer unpaid,
                            @Param("compensatory") Integer compensatory);

    @Transactional
    @Modifying
    @Query("UPDATE TenantLeaveSettings t SET t.country = :country, t.state = :state, " +
            "t.includeNationalHolidays = :includeNational, t.includeStateHolidays = :includeState " +
            "WHERE t.tenantId = :tenantId")
    int updateHolidaySettings(@Param("tenantId") Long tenantId,
                              @Param("country") String country,
                              @Param("state") String state,
                              @Param("includeNational") Boolean includeNational,
                              @Param("includeState") Boolean includeState);
}