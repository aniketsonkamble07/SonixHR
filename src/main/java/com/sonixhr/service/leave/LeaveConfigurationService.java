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
import org.springframework.lang.NonNull;
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
    public TenantLeaveSettings getTenantSettings(@NonNull Long tenantId) {
        TenantLeaveSettings settings = settingsRepository.findById(tenantId).orElse(null);
        if (settings == null) {
            TenantLeaveSettings newSettings = TenantLeaveSettings.builder()
                    .tenantId(tenantId)
                    .build();
            if (newSettings != null) {
                return settingsRepository.save(newSettings);
            }
            throw new BusinessException("Failed to construct default leave settings");
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
    public TenantLeaveSettings updateTenantSettings(@NonNull Long tenantId, LeaveSettingsDTO dto) {
        TenantLeaveSettings settings = getTenantSettings(tenantId);
        
        if (dto.getLeavePolicies() != null) {
            settings.setLeavePolicies(dto.getLeavePolicies());
            
            // Sync legacy fields for compatibility
            com.sonixhr.dto.leave.LeavePolicyDTO casualPolicy = dto.getLeavePolicies().get("CASUAL");
            if (casualPolicy != null && casualPolicy.getDaysPerYear() != null) {
                settings.setCasualLeavePerYear(casualPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO sickPolicy = dto.getLeavePolicies().get("SICK");
            if (sickPolicy != null && sickPolicy.getDaysPerYear() != null) {
                settings.setSickLeavePerYear(sickPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO earnedPolicy = dto.getLeavePolicies().get("EARNED");
            if (earnedPolicy != null && earnedPolicy.getDaysPerYear() != null) {
                settings.setEarnedLeavePerYear(earnedPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO emergencyPolicy = dto.getLeavePolicies().get("EMERGENCY");
            if (emergencyPolicy != null && emergencyPolicy.getDaysPerYear() != null) {
                settings.setEmergencyLeavePerYear(emergencyPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO maternityPolicy = dto.getLeavePolicies().get("MATERNITY");
            if (maternityPolicy != null && maternityPolicy.getDaysPerYear() != null) {
                settings.setMaternityLeavePerYear(maternityPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO paternityPolicy = dto.getLeavePolicies().get("PATERNITY");
            if (paternityPolicy != null && paternityPolicy.getDaysPerYear() != null) {
                settings.setPaternityLeavePerYear(paternityPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO unpaidPolicy = dto.getLeavePolicies().get("UNPAID");
            if (unpaidPolicy != null && unpaidPolicy.getDaysPerYear() != null) {
                settings.setUnpaidLeavePerYear(unpaidPolicy.getDaysPerYear());
            }
            com.sonixhr.dto.leave.LeavePolicyDTO compensatoryPolicy = dto.getLeavePolicies().get("COMPENSATORY");
            if (compensatoryPolicy != null && compensatoryPolicy.getDaysPerYear() != null) {
                settings.setCompensatoryLeavePerYear(compensatoryPolicy.getDaysPerYear());
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
    public Employee updateEmployeeSettings(@NonNull Long tenantId, @NonNull Long employeeId, WeekendConfig weekendConfig, String customWeekendDays) {
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
            if (tenantSettings == null && employee != null && employee.getTenant() != null) {
                Long tId = employee.getTenant().getId();
                if (tId != null) {
                    tenantSettings = settingsRepository.findById(tId).orElse(null);
                }
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
    public boolean isHolidayForTenant(LocalDate date, @NonNull Long tenantId, TenantLeaveSettings settings) {
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

    /**
     * Get all leave policies map for a tenant.
     */
    public Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> getLeavePolicies(@NonNull Long tenantId) {
        TenantLeaveSettings settings = getTenantSettings(tenantId);
        return settings.getLeavePolicies();
    }

    /**
     * Update/insert a policy configuration for a specific leave type.
     */
    @Transactional
    public Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> updateLeavePolicy(@NonNull Long tenantId, String leaveTypeStr, com.sonixhr.dto.leave.LeavePolicyDTO policyUpdate) {
        TenantLeaveSettings settings = getTenantSettings(tenantId);
        Map<String, com.sonixhr.dto.leave.LeavePolicyDTO> policies = settings.getLeavePolicies();
        if (policies == null) {
            policies = TenantLeaveSettings.createDefaultPolicies();
        }

        // Update the policy map for the target leave type
        policies.put(leaveTypeStr.toUpperCase(), policyUpdate);
        settings.setLeavePolicies(policies);

        // Sync legacy daysPerYear field if updated
        Integer days = policyUpdate.getDaysPerYear();
        if (days != null) {
            switch (leaveTypeStr.toUpperCase()) {
                case "CASUAL": settings.setCasualLeavePerYear(days); break;
                case "SICK": settings.setSickLeavePerYear(days); break;
                case "EARNED": settings.setEarnedLeavePerYear(days); break;
                case "EMERGENCY": settings.setEmergencyLeavePerYear(days); break;
                case "MATERNITY": settings.setMaternityLeavePerYear(days); break;
                case "PATERNITY": settings.setPaternityLeavePerYear(days); break;
                case "UNPAID": settings.setUnpaidLeavePerYear(days); break;
                case "COMPENSATORY": settings.setCompensatoryLeavePerYear(days); break;
            }
        }

        settingsRepository.save(settings);
        return policies;
    }

    public LeaveSettingsDTO getTenantSettingsDTO(@NonNull Long tenantId) {
        return convertToSettingsDTO(getTenantSettings(tenantId));
    }

    @Transactional
    public LeaveSettingsDTO updateTenantSettingsDTO(@NonNull Long tenantId, LeaveSettingsDTO dto) {
        return convertToSettingsDTO(updateTenantSettings(tenantId, dto));
    }

    public LeaveSettingsDTO convertToSettingsDTO(TenantLeaveSettings settings) {
        if (settings == null) {
            return null;
        }
        WeekendConfig wkConfig = settings.getWeekendConfig() != null ? settings.getWeekendConfig() : WeekendConfig.SATURDAY_SUNDAY;
        return LeaveSettingsDTO.builder()
                .tenantId(settings.getTenantId())
                .leavePolicies(settings.getLeavePolicies())
                .policiesConfigured(settings.getPoliciesConfigured())
                .weekendConfig(wkConfig)
                .customWeekendDays(settings.getCustomWeekendDays())
                .countWeekendsAsLeave(settings.getCountWeekendsAsLeave())
                .countHolidaysAsLeave(settings.getCountHolidaysAsLeave())
                .casualLeavePerYear(settings.getCasualLeavePerYear())
                .sickLeavePerYear(settings.getSickLeavePerYear())
                .earnedLeavePerYear(settings.getEarnedLeavePerYear())
                .emergencyLeavePerYear(settings.getEmergencyLeavePerYear())
                .maternityLeavePerYear(settings.getMaternityLeavePerYear())
                .paternityLeavePerYear(settings.getPaternityLeavePerYear())
                .unpaidLeavePerYear(settings.getUnpaidLeavePerYear())
                .compensatoryLeavePerYear(settings.getCompensatoryLeavePerYear())
                .maxConsecutiveLeaveDays(settings.getMaxConsecutiveLeaveDays())
                .leaveApprovalRequired(settings.getLeaveApprovalRequired())
                .autoApproveForManager(settings.getAutoApproveForManager())
                .country(settings.getCountry())
                .state(settings.getState())
                .includeNationalHolidays(settings.getIncludeNationalHolidays())
                .includeStateHolidays(settings.getIncludeStateHolidays())
                .workingDays(getWorkingDaysArray(wkConfig, settings.getCustomWeekendDays()))
                .weekendDays(getWeekendDaysArray(wkConfig, settings.getCustomWeekendDays()))
                .weekendDisplay(wkConfig.getDisplayName())
                .timezone(java.util.TimeZone.getDefault().getID())
                .build();
    }

    private String[] getWeekendDaysArray(WeekendConfig config, String customDays) {
        if (config == WeekendConfig.SATURDAY_SUNDAY) {
            return new String[]{"SATURDAY", "SUNDAY"};
        } else if (config == WeekendConfig.SUNDAY_ONLY) {
            return new String[]{"SUNDAY"};
        } else if (config == WeekendConfig.CUSTOM && customDays != null) {
            return Arrays.stream(customDays.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    private String[] getWorkingDaysArray(WeekendConfig config, String customDays) {
        List<String> allDays = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        String[] weekendDays = getWeekendDaysArray(config, customDays);
        List<String> weekendList = Arrays.asList(weekendDays);
        return allDays.stream()
                .filter(d -> !weekendList.contains(d))
                .toArray(String[]::new);
    }
}
