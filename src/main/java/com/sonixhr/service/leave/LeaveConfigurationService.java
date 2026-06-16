package com.sonixhr.service.leave;

import com.sonixhr.dto.leave.LeaveSettingsDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.leave.WeekendConfig;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveConfigurationService {

    private final TenantLeaveSettingsRepository settingsRepository;
    private final EmployeeRepository employeeRepository;
    private final PublicHolidayRepository holidayRepository;

    /**
     * Get tenant leave settings, creates defaults if not present.
     */
    @Transactional
    public TenantLeaveSettings getTenantSettings(Long tenantId) {
        TenantLeaveSettings settings = settingsRepository.findById(tenantId).orElse(null);
        if (settings == null) {
            settings = TenantLeaveSettings.builder()
                    .tenantId(tenantId)
                    .build();
            return settingsRepository.save(settings);
        }
        if (settings.getLeavePolicies() == null || settings.getLeavePolicies().isEmpty()) {
            settings.setLeavePolicies(TenantLeaveSettings.createDefaultPolicies());
            settings = settingsRepository.save(settings);
        }
        return settings;
    }

    /**
     * Update tenant leave settings.
     */
    @Transactional
    public TenantLeaveSettings updateTenantSettings(Long tenantId, LeaveSettingsDTO dto) {
        TenantLeaveSettings settings = getTenantSettings(tenantId);
        
        if (dto.getLeavePolicies() != null) {
            settings.setLeavePolicies(dto.getLeavePolicies());
            
            // Sync legacy fields for compatibility
            Object casualPolicy = dto.getLeavePolicies().get("CASUAL");
            if (casualPolicy instanceof Map) {
                Object days = ((Map<?, ?>) casualPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setCasualLeavePerYear(((Number) days).intValue());
                }
            }
            Object sickPolicy = dto.getLeavePolicies().get("SICK");
            if (sickPolicy instanceof Map) {
                Object days = ((Map<?, ?>) sickPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setSickLeavePerYear(((Number) days).intValue());
                }
            }
            Object earnedPolicy = dto.getLeavePolicies().get("EARNED");
            if (earnedPolicy instanceof Map) {
                Object days = ((Map<?, ?>) earnedPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setEarnedLeavePerYear(((Number) days).intValue());
                }
            }
            Object emergencyPolicy = dto.getLeavePolicies().get("EMERGENCY");
            if (emergencyPolicy instanceof Map) {
                Object days = ((Map<?, ?>) emergencyPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setEmergencyLeavePerYear(((Number) days).intValue());
                }
            }
            Object maternityPolicy = dto.getLeavePolicies().get("MATERNITY");
            if (maternityPolicy instanceof Map) {
                Object days = ((Map<?, ?>) maternityPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setMaternityLeavePerYear(((Number) days).intValue());
                }
            }
            Object paternityPolicy = dto.getLeavePolicies().get("PATERNITY");
            if (paternityPolicy instanceof Map) {
                Object days = ((Map<?, ?>) paternityPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setPaternityLeavePerYear(((Number) days).intValue());
                }
            }
            Object unpaidPolicy = dto.getLeavePolicies().get("UNPAID");
            if (unpaidPolicy instanceof Map) {
                Object days = ((Map<?, ?>) unpaidPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setUnpaidLeavePerYear(((Number) days).intValue());
                }
            }
            Object compensatoryPolicy = dto.getLeavePolicies().get("COMPENSATORY");
            if (compensatoryPolicy instanceof Map) {
                Object days = ((Map<?, ?>) compensatoryPolicy).get("daysPerYear");
                if (days instanceof Number) {
                    settings.setCompensatoryLeavePerYear(((Number) days).intValue());
                }
            }
        }
        if (dto.getWeekendConfig() != null) settings.setWeekendConfig(dto.getWeekendConfig());
        if (dto.getCustomWeekendDays() != null) settings.setCustomWeekendDays(dto.getCustomWeekendDays());
        if (dto.getCountWeekendsAsLeave() != null) settings.setCountWeekendsAsLeave(dto.getCountWeekendsAsLeave());
        if (dto.getCountHolidaysAsLeave() != null) settings.setCountHolidaysAsLeave(dto.getCountHolidaysAsLeave());
        if (dto.getCasualLeavePerYear() != null) settings.setCasualLeavePerYear(dto.getCasualLeavePerYear());
        if (dto.getSickLeavePerYear() != null) settings.setSickLeavePerYear(dto.getSickLeavePerYear());
        if (dto.getEarnedLeavePerYear() != null) settings.setEarnedLeavePerYear(dto.getEarnedLeavePerYear());
        if (dto.getEmergencyLeavePerYear() != null) settings.setEmergencyLeavePerYear(dto.getEmergencyLeavePerYear());
        if (dto.getMaternityLeavePerYear() != null) settings.setMaternityLeavePerYear(dto.getMaternityLeavePerYear());
        if (dto.getPaternityLeavePerYear() != null) settings.setPaternityLeavePerYear(dto.getPaternityLeavePerYear());
        if (dto.getUnpaidLeavePerYear() != null) settings.setUnpaidLeavePerYear(dto.getUnpaidLeavePerYear());
        if (dto.getCompensatoryLeavePerYear() != null) settings.setCompensatoryLeavePerYear(dto.getCompensatoryLeavePerYear());
        if (dto.getMaxConsecutiveLeaveDays() != null) settings.setMaxConsecutiveLeaveDays(dto.getMaxConsecutiveLeaveDays());
        if (dto.getLeaveApprovalRequired() != null) settings.setLeaveApprovalRequired(dto.getLeaveApprovalRequired());
        if (dto.getAutoApproveForManager() != null) settings.setAutoApproveForManager(dto.getAutoApproveForManager());
        if (dto.getCountry() != null) settings.setCountry(dto.getCountry());
        if (dto.getState() != null) settings.setState(dto.getState());
        if (dto.getIncludeNationalHolidays() != null) settings.setIncludeNationalHolidays(dto.getIncludeNationalHolidays());
        if (dto.getIncludeStateHolidays() != null) settings.setIncludeStateHolidays(dto.getIncludeStateHolidays());

        if (dto.getPoliciesConfigured() != null) {
            settings.setPoliciesConfigured(dto.getPoliciesConfigured());
        } else {
            settings.setPoliciesConfigured(true);
        }

        return settingsRepository.save(settings);
    }

    /**
     * Update employee-specific weekend and off-day settings.
     */
    @Transactional
    public Employee updateEmployeeSettings(Long tenantId, Long employeeId, WeekendConfig weekendConfig, String customWeekendDays) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (!employee.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }

        employee.setWeekendConfig(weekendConfig);
        employee.setCustomWeekendDays(customWeekendDays);
        return employeeRepository.save(employee);
    }

    /**
     * Check if a date is a weekend for a specific employee (overrides tenant settings if set).
     */
    public boolean isWeekendForEmployee(LocalDate date, Employee employee, TenantLeaveSettings settings) {
        WeekendConfig config = null;
        String customDays = null;

        // 1. Check employee-level settings first
        if (employee != null) {
            config = employee.getWeekendConfig();
            customDays = employee.getCustomWeekendDays();
        }

        // 2. Fall back to tenant-level settings if employee settings are null
        if (config == null) {
            TenantLeaveSettings tenantSettings = settings;
            if (tenantSettings == null && employee != null) {
                tenantSettings = settingsRepository.findById(employee.getTenant().getId()).orElse(null);
            }
            if (tenantSettings != null) {
                config = tenantSettings.getWeekendConfig();
                customDays = tenantSettings.getCustomWeekendDays();
            }
        }

        // 3. Fall back to standard SATURDAY_SUNDAY if still null
        if (config == null) {
            config = WeekendConfig.SATURDAY_SUNDAY;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        switch (config) {
            case SATURDAY_SUNDAY:
                return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            case SUNDAY_ONLY:
                return dayOfWeek == DayOfWeek.SUNDAY;
            case CUSTOM:
                return isCustomWeekend(date, customDays);
            default:
                return false;
        }
    }

    /**
     * Helper to check custom weekend days.
     */
    private boolean isCustomWeekend(LocalDate date, String customWeekendDays) {
        if (customWeekendDays == null || customWeekendDays.isEmpty()) {
            return false;
        }
        try {
            // Check if day name is contained in configuration
            String dayOfWeek = date.getDayOfWeek().toString();
            return customWeekendDays.toUpperCase().contains(dayOfWeek);
        } catch (Exception e) {
            log.warn("Error parsing custom weekend days: {}", customWeekendDays);
            return false;
        }
    }

    /**
     * Check if a date is a public holiday for the tenant.
     */
    public boolean isHolidayForTenant(LocalDate date, Long tenantId, TenantLeaveSettings settings) {
        if (settings == null) {
            settings = settingsRepository.findById(tenantId).orElse(null);
            if (settings == null) {
                return false;
            }
        }

        if (!settings.getIncludeNationalHolidays() && !settings.getIncludeStateHolidays()) {
            return false;
        }

        List<PublicHoliday> holidays = holidayRepository.findByTenantIdAndHolidayDateBetween(tenantId, date, date);
        if (holidays == null || holidays.isEmpty()) {
            return false;
        }

        for (PublicHoliday h : holidays) {
            boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType()) && settings.getIncludeNationalHolidays();
            boolean isState = settings.getIncludeStateHolidays() && 
                              settings.getState() != null && 
                              settings.getState().equalsIgnoreCase(h.getRegion());
            if (isNational || isState) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a date is either a weekend or holiday/off-day for a specific employee.
     */
    public boolean isWeekendOrHolidayForEmployee(LocalDate date, Employee employee, TenantLeaveSettings settings) {
        if (employee == null) {
            return false;
        }
        
        Long tenantId = employee.getTenantId();
        if (settings == null && tenantId != null) {
            settings = settingsRepository.findById(tenantId).orElse(null);
        }

        boolean isWeekend = isWeekendForEmployee(date, employee, settings);
        if (isWeekend && settings != null && Boolean.TRUE.equals(settings.getCountWeekendsAsLeave())) {
            isWeekend = false;
        }

        boolean isHoliday = false;
        if (tenantId != null) {
            isHoliday = isHolidayForTenant(date, tenantId, settings);
        }
        if (isHoliday && settings != null && Boolean.TRUE.equals(settings.getCountHolidaysAsLeave())) {
            isHoliday = false;
        }

        return isWeekend || isHoliday;
    }
}
