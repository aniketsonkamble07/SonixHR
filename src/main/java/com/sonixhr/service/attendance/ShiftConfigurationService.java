package com.sonixhr.service.attendance;

import com.sonixhr.dto.attendance.ShiftConfigurationDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.entity.attendance.ShiftConfiguration;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftConfigurationService {

    private final ShiftConfigurationRepository shiftConfigurationRepository;

    // =====================================================
    // CREATE SHIFT CONFIGURATION
    // =====================================================

    @Transactional
    public ShiftConfigurationDTO createShiftConfiguration(ShiftConfigurationRequestDTO request, Long userId) {
        log.info("Creating shift configuration for tenant: {}", request.getTenantId());

        if (shiftConfigurationRepository.existsByTenantId(request.getTenantId())) {
            throw new BusinessException("Shift configuration already exists for this tenant");
        }

        validateShiftTimings(request.getStartTime(), request.getEndTime());

        if (request.getShiftCode() != null && shiftConfigurationRepository.existsByShiftCode(request.getShiftCode())) {
            throw new BusinessException("Shift code already exists: " + request.getShiftCode());
        }

        validateWorkingHoursThresholds(request.getFullDayHours(), request.getHalfDayHours(), request.getQuarterDayHours());
        validateBreakDurations(request.getBreakDurationMinutes(), request.getMinBreakMinutes(), request.getMaxBreakMinutes());

        ShiftConfiguration shift = ShiftConfiguration.builder()
                .tenantId(request.getTenantId())
                .shiftName(request.getShiftName())
                .shiftCode(request.getShiftCode())
                .shiftDescription(request.getShiftDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .totalHours(calculateTotalHours(request.getStartTime(), request.getEndTime()))
                .breakDurationMinutes(request.getBreakDurationMinutes() != null ? request.getBreakDurationMinutes() : 60)
                .minBreakMinutes(request.getMinBreakMinutes() != null ? request.getMinBreakMinutes() : 30)
                .maxBreakMinutes(request.getMaxBreakMinutes() != null ? request.getMaxBreakMinutes() : 90)
                .lateGraceMinutes(request.getLateGraceMinutes() != null ? request.getLateGraceMinutes() : 15)
                .earlyExitGraceMinutes(request.getEarlyExitGraceMinutes() != null ? request.getEarlyExitGraceMinutes() : 15)
                .checkinBufferBefore(request.getCheckinBufferBefore() != null ? request.getCheckinBufferBefore() : 60)
                .checkoutBufferAfter(request.getCheckoutBufferAfter() != null ? request.getCheckoutBufferAfter() : 60)
                .fullDayHours(request.getFullDayHours() != null ? request.getFullDayHours() : 8.0)
                .halfDayHours(request.getHalfDayHours() != null ? request.getHalfDayHours() : 4.0)
                .quarterDayHours(request.getQuarterDayHours() != null ? request.getQuarterDayHours() : 2.0)
                .allowOvertime(request.getAllowOvertime() != null ? request.getAllowOvertime() : true)
                .overtimeMultiplier(request.getOvertimeMultiplier() != null ? request.getOvertimeMultiplier() : 1.5)
                .overtimeThresholdMinutes(request.getOvertimeThresholdMinutes() != null ? request.getOvertimeThresholdMinutes() : 30)
                .maxOvertimeHoursPerDay(request.getMaxOvertimeHoursPerDay() != null ? request.getMaxOvertimeHoursPerDay() : 4.0)
                .weeklyOffs(request.getWeeklyOffs() != null ? String.join(",", request.getWeeklyOffs()) : null)
                .alternateWeekOff(request.getAlternateWeekOff() != null ? request.getAlternateWeekOff() : false)
                .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now())
                .effectiveTo(request.getEffectiveTo())
                .isActive(true)
                .isDefault(!shiftConfigurationRepository.existsByTenantId(request.getTenantId()))
                .createdBy(userId)
                .build();

        ShiftConfiguration saved = shiftConfigurationRepository.save(shift);
        log.info("Shift configuration created with id: {}", saved.getId());

        return convertToDTO(saved);
    }

    // =====================================================
    // UPDATE SHIFT CONFIGURATION
    // =====================================================

    @Transactional
    public ShiftConfigurationDTO updateShiftConfiguration(Long id, ShiftConfigurationRequestDTO request, Long userId) {
        log.info("Updating shift configuration: {}", id);

        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found with id: " + id));

        validateShiftTimings(request.getStartTime(), request.getEndTime());
        validateWorkingHoursThresholds(request.getFullDayHours(), request.getHalfDayHours(), request.getQuarterDayHours());
        validateBreakDurations(request.getBreakDurationMinutes(), request.getMinBreakMinutes(), request.getMaxBreakMinutes());

        if (request.getShiftCode() != null && !request.getShiftCode().equals(shift.getShiftCode())) {
            if (shiftConfigurationRepository.existsByShiftCode(request.getShiftCode())) {
                throw new BusinessException("Shift code already exists: " + request.getShiftCode());
            }
        }

        shift.setShiftName(request.getShiftName());
        shift.setShiftCode(request.getShiftCode());
        shift.setShiftDescription(request.getShiftDescription());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setTotalHours(calculateTotalHours(request.getStartTime(), request.getEndTime()));
        shift.setBreakDurationMinutes(request.getBreakDurationMinutes());
        shift.setMinBreakMinutes(request.getMinBreakMinutes());
        shift.setMaxBreakMinutes(request.getMaxBreakMinutes());
        shift.setLateGraceMinutes(request.getLateGraceMinutes());
        shift.setEarlyExitGraceMinutes(request.getEarlyExitGraceMinutes());
        shift.setCheckinBufferBefore(request.getCheckinBufferBefore());
        shift.setCheckoutBufferAfter(request.getCheckoutBufferAfter());
        shift.setFullDayHours(request.getFullDayHours());
        shift.setHalfDayHours(request.getHalfDayHours());
        shift.setQuarterDayHours(request.getQuarterDayHours());
        shift.setAllowOvertime(request.getAllowOvertime());
        shift.setOvertimeMultiplier(request.getOvertimeMultiplier());
        shift.setOvertimeThresholdMinutes(request.getOvertimeThresholdMinutes());
        shift.setMaxOvertimeHoursPerDay(request.getMaxOvertimeHoursPerDay());

        if (request.getWeeklyOffs() != null) {
            shift.setWeeklyOffs(String.join(",", request.getWeeklyOffs()));
        }

        shift.setAlternateWeekOff(request.getAlternateWeekOff());
        shift.setEffectiveFrom(request.getEffectiveFrom());
        shift.setEffectiveTo(request.getEffectiveTo());
        shift.setUpdatedBy(userId);

        ShiftConfiguration saved = shiftConfigurationRepository.save(shift);
        log.info("Shift configuration updated: {}", id);

        return convertToDTO(saved);
    }

    // =====================================================
    // GET SHIFT CONFIGURATION - ALL RETURN DTO
    // =====================================================

    public ShiftConfigurationDTO getShiftConfiguration(UUID tenantId) {
        ShiftConfiguration shift = shiftConfigurationRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found for tenant: " + tenantId));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getShiftConfigurationById(Long id) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found with id: " + id));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getShiftByCode(String shiftCode) {
        ShiftConfiguration shift = shiftConfigurationRepository.findByShiftCode(shiftCode)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found with code: " + shiftCode));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getEffectiveShiftOnDate(UUID tenantId, LocalDate date) {
        ShiftConfiguration shift = shiftConfigurationRepository.findEffectiveOnDate(tenantId, date)
                .orElseThrow(() -> new ResourceNotFoundException("No effective shift configuration found for date: " + date));
        return convertToDTO(shift);
    }

    public List<ShiftConfigurationDTO> getAllShiftConfigurations(UUID tenantId) {
        return shiftConfigurationRepository.findAllByTenantIdOrderByEffectiveFromDesc(tenantId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ShiftConfigurationDTO> getAllActiveShifts() {
        return shiftConfigurationRepository.findAllByIsActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // =====================================================
    // SHIFT VALIDATION & UTILITY METHODS - USING DTO
    // =====================================================

    public boolean isValidCheckinTime(UUID tenantId, LocalTime checkinTime, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        LocalTime earliestCheckin = shift.getStartTime().minusMinutes(shift.getCheckinBufferBefore());
        if (checkinTime.isBefore(earliestCheckin)) {
            return false;
        }

        LocalTime latestCheckin = shift.getEndTime().plusMinutes(shift.getCheckoutBufferAfter());
        if (checkinTime.isAfter(latestCheckin)) {
            return false;
        }

        return true;
    }

    public int calculateLateMinutes(UUID tenantId, LocalTime checkinTime, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);
        LocalTime graceTime = shift.getStartTime().plusMinutes(shift.getLateGraceMinutes());

        if (checkinTime.isAfter(graceTime)) {
            return (int) java.time.Duration.between(shift.getStartTime(), checkinTime).toMinutes();
        }

        return 0;
    }

    public int calculateEarlyExitMinutes(UUID tenantId, LocalTime checkoutTime, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);
        LocalTime graceTime = shift.getEndTime().minusMinutes(shift.getEarlyExitGraceMinutes());

        if (checkoutTime.isBefore(graceTime)) {
            return (int) java.time.Duration.between(checkoutTime, shift.getEndTime()).toMinutes();
        }

        return 0;
    }

    public double calculateWorkingHours(LocalTime checkIn, LocalTime checkOut, Integer breakMinutes) {
        if (checkIn == null || checkOut == null) {
            return 0.0;
        }

        long totalMinutes = java.time.Duration.between(checkIn, checkOut).toMinutes();
        long effectiveMinutes = totalMinutes - (breakMinutes != null ? breakMinutes : 0);

        if (effectiveMinutes < 0) effectiveMinutes = 0;

        return Math.round((effectiveMinutes / 60.0) * 100.0) / 100.0;
    }

    public double calculateOvertime(UUID tenantId, LocalTime checkoutTime, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (!shift.getAllowOvertime() || checkoutTime == null) {
            return 0.0;
        }

        LocalTime overtimeStart = shift.getEndTime().plusMinutes(shift.getOvertimeThresholdMinutes());

        if (checkoutTime.isAfter(overtimeStart)) {
            long overtimeMinutes = java.time.Duration.between(shift.getEndTime(), checkoutTime).toMinutes();
            double overtimeHours = overtimeMinutes / 60.0;

            if (overtimeHours > shift.getMaxOvertimeHoursPerDay()) {
                overtimeHours = shift.getMaxOvertimeHoursPerDay();
            }

            return Math.round(overtimeHours * 100.0) / 100.0;
        }

        return 0.0;
    }

    public String determineStatus(UUID tenantId, LocalTime checkinTime, LocalDate date, Double workingHours) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (checkinTime == null) {
            return "ABSENT";
        }

        LocalTime graceTime = shift.getStartTime().plusMinutes(shift.getLateGraceMinutes());

        if (checkinTime.isAfter(graceTime)) {
            return "LATE";
        }

        if (workingHours != null && workingHours <= shift.getHalfDayHours()) {
            return "HALF_DAY";
        }

        return "PRESENT";
    }

    public boolean isWeekOff(UUID tenantId, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (shift.getWeeklyOffs() == null || shift.getWeeklyOffs().isEmpty()) {
            return false;
        }

        String dayName = date.getDayOfWeek().toString();
        List<String> weeklyOffs = shift.getWeeklyOffsList();

        if (weeklyOffs.contains(dayName)) {
            return true;
        }

        if (shift.getAlternateWeekOff() != null && shift.getAlternateWeekOff()) {
            int weekNumber = (date.getDayOfMonth() - 1) / 7 + 1;
            return weekNumber % 2 == 0 && weeklyOffs.contains(dayName);
        }

        return false;
    }

    public double getExpectedWorkingHours(UUID tenantId, LocalDate date) {
        if (isWeekOff(tenantId, date)) {
            return 0.0;
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);
        return shift.getFullDayHours();
    }

    // =====================================================
    // ACTIVATE/DEACTIVATE SHIFT
    // =====================================================

    @Transactional
    public void activateShift(Long id, Long userId) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found"));

        shiftConfigurationRepository.deactivateAllShiftsForTenant(shift.getTenantId());
        shift.setIsActive(true);
        shift.setUpdatedBy(userId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration activated: {}", id);
    }

    @Transactional
    public void deactivateShift(Long id, Long userId) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found"));

        shift.setIsActive(false);
        shift.setUpdatedBy(userId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration deactivated: {}", id);
    }

    @Transactional
    public void setDefaultShift(Long id, Long userId) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found"));

        List<ShiftConfiguration> tenantShifts = shiftConfigurationRepository.findAllByTenantIdOrderByEffectiveFromDesc(shift.getTenantId());
        tenantShifts.forEach(s -> {
            s.setIsDefault(false);
            shiftConfigurationRepository.save(s);
        });

        shift.setIsDefault(true);
        shift.setUpdatedBy(userId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration set as default: {}", id);
    }

    // =====================================================
    // DELETE SHIFT CONFIGURATION
    // =====================================================

    @Transactional
    public void softDeleteShiftConfiguration(Long id, Long userId) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found with id: " + id));

        shift.setIsActive(false);
        shift.setUpdatedBy(userId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration deactivated (soft delete): {}", id);
    }

    @Transactional
    public void hardDeleteShiftConfiguration(Long id) {
        ShiftConfiguration shift = shiftConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift configuration not found with id: " + id));

        shiftConfigurationRepository.delete(shift);
        log.info("Shift configuration permanently deleted: {}", id);
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private void validateShiftTimings(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException("Start time and end time are required");
        }
        if (startTime.equals(endTime)) {
            throw new BusinessException("Start time and end time cannot be the same");
        }
    }

    private void validateWorkingHoursThresholds(Double fullDay, Double halfDay, Double quarterDay) {
        if (fullDay != null && halfDay != null && halfDay >= fullDay) {
            throw new BusinessException("Half day hours must be less than full day hours");
        }
        if (halfDay != null && quarterDay != null && quarterDay >= halfDay) {
            throw new BusinessException("Quarter day hours must be less than half day hours");
        }
    }

    private void validateBreakDurations(Integer breakDuration, Integer minBreak, Integer maxBreak) {
        if (minBreak != null && maxBreak != null && minBreak > maxBreak) {
            throw new BusinessException("Minimum break cannot exceed maximum break");
        }
        if (breakDuration != null && minBreak != null && breakDuration < minBreak) {
            throw new BusinessException("Break duration cannot be less than minimum break");
        }
        if (breakDuration != null && maxBreak != null && breakDuration > maxBreak) {
            throw new BusinessException("Break duration cannot exceed maximum break");
        }
    }

    private Double calculateTotalHours(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) return 0.0;

        if (endTime.isBefore(startTime)) {
            double hours1 = (24 - startTime.getHour()) + (startTime.getMinute() / 60.0);
            double hours2 = endTime.getHour() + (endTime.getMinute() / 60.0);
            return Math.round((hours1 + hours2) * 100.0) / 100.0;
        } else {
            double hours = (endTime.getHour() - startTime.getHour()) +
                    (endTime.getMinute() - startTime.getMinute()) / 60.0;
            return Math.round(hours * 100.0) / 100.0;
        }
    }

    private ShiftConfigurationDTO convertToDTO(ShiftConfiguration shift) {
        if (shift == null) return null;

        return ShiftConfigurationDTO.builder()
                .id(shift.getId())
                .tenantId(shift.getTenantId())
                .shiftName(shift.getShiftName())
                .shiftCode(shift.getShiftCode())
                .shiftDescription(shift.getShiftDescription())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .totalHours(shift.getTotalHours())
                .breakDurationMinutes(shift.getBreakDurationMinutes())
                .minBreakMinutes(shift.getMinBreakMinutes())
                .maxBreakMinutes(shift.getMaxBreakMinutes())
                .lateGraceMinutes(shift.getLateGraceMinutes())
                .earlyExitGraceMinutes(shift.getEarlyExitGraceMinutes())
                .checkinBufferBefore(shift.getCheckinBufferBefore())
                .checkoutBufferAfter(shift.getCheckoutBufferAfter())
                .fullDayHours(shift.getFullDayHours())
                .halfDayHours(shift.getHalfDayHours())
                .quarterDayHours(shift.getQuarterDayHours())
                .allowOvertime(shift.getAllowOvertime())
                .overtimeMultiplier(shift.getOvertimeMultiplier())
                .overtimeThresholdMinutes(shift.getOvertimeThresholdMinutes())
                .maxOvertimeHoursPerDay(shift.getMaxOvertimeHoursPerDay())
                .weeklyOffs(shift.getWeeklyOffs())
                .alternateWeekOff(shift.getAlternateWeekOff())
                .effectiveFrom(shift.getEffectiveFrom())
                .effectiveTo(shift.getEffectiveTo())
                .isActive(shift.getIsActive())
                .isDefault(shift.getIsDefault())
                .createdAt(shift.getCreatedAt())
                .updatedAt(shift.getUpdatedAt())
                .build();
    }
}