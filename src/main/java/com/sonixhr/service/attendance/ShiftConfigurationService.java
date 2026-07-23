package com.sonixhr.service.attendance;

import com.sonixhr.dto.attendance.ShiftConfigurationDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationSummaryDTO;
import com.sonixhr.entity.attendance.ShiftConfiguration;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.attendance.ShiftConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftConfigurationService {

    private final ShiftConfigurationRepository shiftConfigurationRepository;

    // =====================================================
    // CREATE OPERATIONS
    // =====================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ShiftConfigurationDTO createShiftConfiguration(
            ShiftConfigurationRequestDTO request,
            Long tenantId,
            Long employeeId) {

        log.info("Creating shift configuration for tenant: {} by employee: {}", tenantId, employeeId);

        try {
            validateRequest(request);

            boolean shiftExists = shiftConfigurationRepository.existsByTenantIdAndIsDeletedFalse(tenantId);
            if (shiftExists) {
                log.warn("Shift configuration already exists for tenant: {}", tenantId);
                ShiftConfiguration existing = shiftConfigurationRepository
                        .findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                        .orElse(null);
                if (existing != null) {
                    return convertToDTO(existing);
                }
                throw new BusinessException("Shift configuration already exists for this tenant");
            }

            if (request.getShiftCode() != null && !request.getShiftCode().isEmpty()) {
                if (shiftConfigurationRepository.existsByShiftCodeAndTenantIdAndIsDeletedFalse(
                        request.getShiftCode(), tenantId)) {
                    throw new BusinessException("Shift code already exists for this tenant: " + request.getShiftCode());
                }
            }

            ShiftConfiguration shift = buildShiftEntity(request, tenantId, employeeId);
            shift.setIsDefault(!shiftConfigurationRepository.existsByTenantIdAndIsDeletedFalse(tenantId));

            ShiftConfiguration saved = shiftConfigurationRepository.save(shift);
            log.info("Shift configuration created with id: {} for tenant: {}", saved.getId(), tenantId);

            return convertToDTO(saved);

        } catch (Exception e) {
            log.error("Failed to create shift configuration for tenant: {}", tenantId, e);
            throw new BusinessException("Failed to create shift configuration: " + e.getMessage());
        }
    }

    /**
     * Create default shift in a separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ShiftConfiguration createDefaultShift(Long tenantId, Long employeeId) {
        log.info("Creating default shift for tenant: {}", tenantId);

        try {
            boolean shiftExists = shiftConfigurationRepository.existsByTenantIdAndIsDeletedFalse(tenantId);
            if (shiftExists) {
                log.debug("Shift already exists for tenant: {}", tenantId);
                return shiftConfigurationRepository
                        .findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                        .orElse(null);
            }

            ShiftConfiguration shift = ShiftConfiguration.builder()
                    .tenantId(tenantId)
                    .shiftName("Default Shift")
                    .shiftCode("DEFAULT")
                    .shiftDescription("Standard working hours (9 AM - 5 PM)")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .totalHours(8.0)
                    .breakDurationMinutes(60)
                    .minBreakMinutes(30)
                    .maxBreakMinutes(90)
                    .lateGraceMinutes(15)
                    .earlyExitGraceMinutes(15)
                    .checkinBufferBefore(60)
                    .checkoutBufferAfter(60)
                    .fullDayHours(8.0)
                    .halfDayHours(4.0)
                    .quarterDayHours(2.0)
                    .allowOvertime(true)
                    .overtimeMultiplier(1.5)
                    .overtimeThresholdMinutes(30)
                    .maxOvertimeHoursPerDay(4.0)
                    .weeklyOffs("SATURDAY,SUNDAY")
                    .alternateWeekOff(false)
                    .effectiveFrom(LocalDate.now())
                    .effectiveTo(null)
                    .isActive(true)
                    .isDefault(true)
                    .isDeleted(false)
                    .createdBy(employeeId)
                    .updatedBy(employeeId)
                    .build();

            ShiftConfiguration saved = shiftConfigurationRepository.save(shift);
            log.info("Default shift created with id: {} for tenant: {}", saved.getId(), tenantId);
            return saved;

        } catch (Exception e) {
            log.error("Failed to create default shift for tenant: {}", tenantId, e);
            return null;
        }
    }

    // =====================================================
    // UPDATE OPERATIONS
    // =====================================================

    @Transactional
    public ShiftConfigurationDTO updateShiftConfiguration(
            Long id,
            ShiftConfigurationRequestDTO request,
            Long tenantId,
            Long employeeId) {

        log.info("Updating shift configuration: {} for tenant: {} by employee: {}", id, tenantId, employeeId);

        validateRequest(request);

        ShiftConfiguration shift = getShiftEntity(id, tenantId);

        if (request.getShiftCode() != null && !request.getShiftCode().equals(shift.getShiftCode())) {
            if (shiftConfigurationRepository.existsByShiftCodeAndTenantIdAndIsDeletedFalse(
                    request.getShiftCode(), tenantId)) {
                throw new BusinessException("Shift code already exists for this tenant: " + request.getShiftCode());
            }
        }

        updateShiftEntity(shift, request, employeeId);

        ShiftConfiguration saved = shiftConfigurationRepository.save(shift);
        log.info("Shift configuration updated: {} for tenant: {}", id, tenantId);

        return convertToDTO(saved);
    }

    // =====================================================
    // ACTIVATION / DEACTIVATION
    // =====================================================

    @Transactional
    public void activateShift(Long id, Long tenantId, Long employeeId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);
        shift.setIsActive(true);
        shift.setUpdatedBy(employeeId);
        shiftConfigurationRepository.save(shift);
        log.info("Shift configuration activated: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
    }

    @Transactional
    public void deactivateShift(Long id, Long tenantId, Long employeeId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);

        if (Boolean.TRUE.equals(shift.getIsDefault())) {
            throw new BusinessException("Cannot deactivate default shift. Please set another shift as default first.");
        }

        shift.setIsActive(false);
        shift.setUpdatedBy(employeeId);
        shiftConfigurationRepository.save(shift);
        log.info("Shift configuration deactivated: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
    }

    @Transactional
    public void setDefaultShift(Long id, Long tenantId, Long employeeId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);

        shiftConfigurationRepository.resetAllDefaultForTenant(tenantId);

        shift.setIsDefault(true);
        shift.setUpdatedBy(employeeId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration set as default: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
    }

    // =====================================================
    // DELETE OPERATIONS
    // =====================================================

    @Transactional
    public void softDeleteShiftConfiguration(Long id, Long tenantId, Long employeeId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);

        if (Boolean.TRUE.equals(shift.getIsDefault())) {
            throw new BusinessException("Cannot delete default shift. Please set another shift as default first.");
        }

        shift.setIsActive(false);
        shift.setIsDeleted(true);
        shift.setUpdatedBy(employeeId);
        shiftConfigurationRepository.save(shift);

        log.info("Shift configuration soft deleted: {} for tenant: {} by employee: {}", id, tenantId, employeeId);
    }

    @Transactional
    public void hardDeleteShiftConfiguration(Long id, Long tenantId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);

        if (Boolean.TRUE.equals(shift.getIsDefault())) {
            throw new BusinessException("Cannot delete default shift. Please set another shift as default first.");
        }

        shiftConfigurationRepository.delete(shift);
        log.info("Shift configuration permanently deleted: {} for tenant: {}", id, tenantId);
    }

    // =====================================================
    // GET OPERATIONS
    // =====================================================

    public ShiftConfigurationDTO getShiftConfiguration(Long tenantId) {
        ShiftConfiguration shift = shiftConfigurationRepository
                .findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift configuration not found for tenant: " + tenantId));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getShiftConfigurationById(Long id, Long tenantId) {
        ShiftConfiguration shift = getShiftEntity(id, tenantId);
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getShiftByCode(String shiftCode, Long tenantId) {
        ShiftConfiguration shift = shiftConfigurationRepository
                .findByShiftCodeAndTenantIdAndIsDeletedFalse(shiftCode, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift configuration not found with code: " + shiftCode));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getEffectiveShiftOnDate(Long tenantId, LocalDate date) {
        ShiftConfiguration shift = shiftConfigurationRepository
                .findEffectiveOnDate(tenantId, date)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No effective shift configuration found for date: " + date));
        return convertToDTO(shift);
    }

    public ShiftConfigurationDTO getDefaultShift(Long tenantId) {
        ShiftConfiguration shift = shiftConfigurationRepository
                .findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No default shift configured for tenant: " + tenantId));
        return convertToDTO(shift);
    }

    public List<ShiftConfigurationDTO> getAllShiftConfigurations(Long tenantId) {
        return shiftConfigurationRepository
                .findAllByTenantIdAndIsDeletedFalseOrderByEffectiveFromDesc(tenantId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ShiftConfigurationDTO> getAllActiveShifts(Long tenantId) {
        return shiftConfigurationRepository
                .findAllByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ShiftConfigurationSummaryDTO> getAllShiftConfigurationsSummary(Long tenantId) {
        return getAllShiftConfigurations(tenantId).stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    public List<ShiftConfigurationSummaryDTO> getAllActiveShiftsSummary(Long tenantId) {
        return getAllActiveShifts(tenantId).stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    // =====================================================
    // ATTENDANCE CALCULATION METHODS
    // =====================================================

    public boolean isValidCheckinTime(Long tenantId, LocalTime checkinTime, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (checkinTime == null) {
            return false;
        }

        if (isWeekOff(tenantId, date)) {
            return false;
        }

        LocalTime earliestCheckin = shift.getStartTime()
                .minusMinutes(shift.getCheckinBufferBefore() != null ? shift.getCheckinBufferBefore() : 0);

        return !checkinTime.isBefore(earliestCheckin);
    }

    public int calculateLateMinutes(Long tenantId, LocalTime checkinTime, LocalDate date) {
        if (checkinTime == null) {
            return 0;
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (isWeekOff(tenantId, date)) {
            return 0;
        }

        LocalTime lateThreshold = shift.getStartTime()
                .plusMinutes(shift.getLateGraceMinutes() != null ? shift.getLateGraceMinutes() : 0);

        if (checkinTime.isAfter(lateThreshold)) {
            return (int) Duration.between(shift.getStartTime(), checkinTime).toMinutes();
        }
        return 0;
    }

    public int calculateEarlyExitMinutes(Long tenantId, LocalTime checkoutTime, LocalDate date) {
        if (checkoutTime == null) {
            return 0;
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (isWeekOff(tenantId, date)) {
            return 0;
        }

        LocalTime earlyExitThreshold = shift.getEndTime()
                .minusMinutes(shift.getEarlyExitGraceMinutes() != null ? shift.getEarlyExitGraceMinutes() : 0);

        if (checkoutTime.isBefore(earlyExitThreshold)) {
            return (int) Duration.between(checkoutTime, shift.getEndTime()).toMinutes();
        }
        return 0;
    }

    public double calculateWorkingHours(LocalTime checkIn, LocalTime checkOut, Integer breakMinutes) {
        if (checkIn == null || checkOut == null) {
            return 0.0;
        }

        long totalMinutes = Duration.between(checkIn, checkOut).toMinutes();
        long effectiveMinutes = totalMinutes - (breakMinutes != null ? breakMinutes : 0);

        if (effectiveMinutes < 0) {
            effectiveMinutes = 0;
        }

        return Math.round((effectiveMinutes / 60.0) * 100.0) / 100.0;
    }

    public double calculateOvertime(Long tenantId, LocalTime checkoutTime, LocalDate date) {
        if (checkoutTime == null) {
            return 0.0;
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (!Boolean.TRUE.equals(shift.getAllowOvertime()) || isWeekOff(tenantId, date)) {
            return 0.0;
        }

        int overtimeThreshold = shift.getOvertimeThresholdMinutes() != null
                ? shift.getOvertimeThresholdMinutes()
                : 30;

        LocalTime overtimeStart = shift.getEndTime().plusMinutes(overtimeThreshold);

        if (checkoutTime.isAfter(overtimeStart)) {
            long overtimeMinutes = Duration.between(shift.getEndTime(), checkoutTime).toMinutes();
            double overtimeHours = overtimeMinutes / 60.0;

            Double maxOvertime = shift.getMaxOvertimeHoursPerDay();
            if (maxOvertime != null && overtimeHours > maxOvertime) {
                overtimeHours = maxOvertime;
            }

            return Math.round(overtimeHours * 100.0) / 100.0;
        }
        return 0.0;
    }

    public String determineStatus(Long tenantId, LocalTime checkinTime, LocalDate date, Double workingHours) {
        if (isWeekOff(tenantId, date)) {
            return "WEEK_OFF";
        }

        if (checkinTime == null) {
            return "ABSENT";
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        LocalTime lateThreshold = shift.getStartTime()
                .plusMinutes(shift.getLateGraceMinutes() != null ? shift.getLateGraceMinutes() : 0);

        if (checkinTime.isAfter(lateThreshold)) {
            return "LATE";
        }

        if (workingHours != null && workingHours <= shift.getHalfDayHours()) {
            return "HALF_DAY";
        }

        return "PRESENT";
    }

    public boolean isWeekOff(Long tenantId, LocalDate date) {
        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);

        if (shift.getWeeklyOffs() == null || shift.getWeeklyOffs().isEmpty()) {
            return false;
        }

        String dayName = date.getDayOfWeek().toString();
        String[] weeklyOffs = shift.getWeeklyOffs().split(",");

        for (String weekOff : weeklyOffs) {
            if (weekOff.trim().equalsIgnoreCase(dayName)) {
                return true;
            }
        }

        if (Boolean.TRUE.equals(shift.getAlternateWeekOff())) {
            int weekNumber = (date.getDayOfMonth() - 1) / 7 + 1;
            return weekNumber % 2 == 0;
        }

        return false;
    }

    public double getExpectedWorkingHours(Long tenantId, LocalDate date) {
        if (isWeekOff(tenantId, date)) {
            return 0.0;
        }

        ShiftConfigurationDTO shift = getEffectiveShiftOnDate(tenantId, date);
        return shift.getFullDayHours() != null ? shift.getFullDayHours() : 8.0;
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private void validateRequest(ShiftConfigurationRequestDTO request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("Start time and end time are required");
        }

        if (request.getStartTime().equals(request.getEndTime())) {
            throw new BusinessException("Start time and end time cannot be the same");
        }

        if (request.getFullDayHours() != null && request.getHalfDayHours() != null) {
            if (request.getHalfDayHours() >= request.getFullDayHours()) {
                throw new BusinessException("Half day hours must be less than full day hours");
            }
        }

        if (request.getHalfDayHours() != null && request.getQuarterDayHours() != null) {
            if (request.getQuarterDayHours() >= request.getHalfDayHours()) {
                throw new BusinessException("Quarter day hours must be less than half day hours");
            }
        }

        if (request.getMinBreakMinutes() != null && request.getMaxBreakMinutes() != null) {
            if (request.getMinBreakMinutes() > request.getMaxBreakMinutes()) {
                throw new BusinessException("Minimum break cannot exceed maximum break");
            }
        }

        if (request.getBreakDurationMinutes() != null && request.getMinBreakMinutes() != null) {
            if (request.getBreakDurationMinutes() < request.getMinBreakMinutes()) {
                throw new BusinessException("Break duration cannot be less than minimum break");
            }
        }

        if (request.getBreakDurationMinutes() != null && request.getMaxBreakMinutes() != null) {
            if (request.getBreakDurationMinutes() > request.getMaxBreakMinutes()) {
                throw new BusinessException("Break duration cannot exceed maximum break");
            }
        }

        if (request.getEffectiveFrom() != null && request.getEffectiveTo() != null &&
                request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {
            throw new BusinessException("Effective from date must be before effective to date");
        }
    }

    // ✅ buildShiftEntity - weeklyOffs is now a String, set it directly
    private ShiftConfiguration buildShiftEntity(ShiftConfigurationRequestDTO request, Long tenantId, Long employeeId) {
        ShiftConfiguration shift = new ShiftConfiguration();

        shift.setTenantId(tenantId);
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

        // ✅ weeklyOffs is already a String from the DTO's custom setter
        shift.setWeeklyOffs(request.getWeeklyOffs());

        shift.setAlternateWeekOff(request.getAlternateWeekOff());
        shift.setEffectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now());
        shift.setEffectiveTo(request.getEffectiveTo());
        shift.setIsActive(true);
        shift.setIsDeleted(false);
        shift.setCreatedBy(employeeId);
        shift.setUpdatedBy(employeeId);

        return shift;
    }

    // ✅ updateShiftEntity - weeklyOffs is now a String, set it directly
    private void updateShiftEntity(ShiftConfiguration shift, ShiftConfigurationRequestDTO request, Long employeeId) {
        if (request.getShiftName() != null) shift.setShiftName(request.getShiftName());
        if (request.getShiftCode() != null) shift.setShiftCode(request.getShiftCode());
        if (request.getShiftDescription() != null) shift.setShiftDescription(request.getShiftDescription());
        if (request.getStartTime() != null) {
            shift.setStartTime(request.getStartTime());
            shift.setTotalHours(calculateTotalHours(request.getStartTime(), shift.getEndTime()));
        }
        if (request.getEndTime() != null) {
            shift.setEndTime(request.getEndTime());
            shift.setTotalHours(calculateTotalHours(shift.getStartTime(), request.getEndTime()));
        }
        if (request.getBreakDurationMinutes() != null) shift.setBreakDurationMinutes(request.getBreakDurationMinutes());
        if (request.getMinBreakMinutes() != null) shift.setMinBreakMinutes(request.getMinBreakMinutes());
        if (request.getMaxBreakMinutes() != null) shift.setMaxBreakMinutes(request.getMaxBreakMinutes());
        if (request.getLateGraceMinutes() != null) shift.setLateGraceMinutes(request.getLateGraceMinutes());
        if (request.getEarlyExitGraceMinutes() != null) shift.setEarlyExitGraceMinutes(request.getEarlyExitGraceMinutes());
        if (request.getCheckinBufferBefore() != null) shift.setCheckinBufferBefore(request.getCheckinBufferBefore());
        if (request.getCheckoutBufferAfter() != null) shift.setCheckoutBufferAfter(request.getCheckoutBufferAfter());
        if (request.getFullDayHours() != null) shift.setFullDayHours(request.getFullDayHours());
        if (request.getHalfDayHours() != null) shift.setHalfDayHours(request.getHalfDayHours());
        if (request.getQuarterDayHours() != null) shift.setQuarterDayHours(request.getQuarterDayHours());
        if (request.getAllowOvertime() != null) shift.setAllowOvertime(request.getAllowOvertime());
        if (request.getOvertimeMultiplier() != null) shift.setOvertimeMultiplier(request.getOvertimeMultiplier());
        if (request.getOvertimeThresholdMinutes() != null) shift.setOvertimeThresholdMinutes(request.getOvertimeThresholdMinutes());
        if (request.getMaxOvertimeHoursPerDay() != null) shift.setMaxOvertimeHoursPerDay(request.getMaxOvertimeHoursPerDay());

        // ✅ weeklyOffs is already a String from the DTO's custom setter
        if (request.getWeeklyOffs() != null) {
            shift.setWeeklyOffs(request.getWeeklyOffs());
        }

        if (request.getAlternateWeekOff() != null) shift.setAlternateWeekOff(request.getAlternateWeekOff());
        if (request.getEffectiveFrom() != null) shift.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) shift.setEffectiveTo(request.getEffectiveTo());

        shift.setUpdatedBy(employeeId);
    }

    private ShiftConfiguration getShiftEntity(Long id, Long tenantId) {
        return shiftConfigurationRepository
                .findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift configuration not found with id: " + id + " for tenant: " + tenantId));
    }

    private Double calculateTotalHours(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
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
                .createdBy(shift.getCreatedBy())
                .updatedBy(shift.getUpdatedBy())
                .build();
    }

    private ShiftConfigurationSummaryDTO toSummaryDTO(ShiftConfigurationDTO dto) {
        return ShiftConfigurationSummaryDTO.builder()
                .id(dto.getId())
                .shiftName(dto.getShiftName())
                .shiftCode(dto.getShiftCode())
                .shiftTimeDisplay(dto.getShiftTimeFormatted())
                .shiftDurationDisplay(dto.getShiftDurationFormatted())
                .isActive(dto.getIsActive())
                .isDefault(dto.getIsDefault())
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveTo(dto.getEffectiveTo())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}