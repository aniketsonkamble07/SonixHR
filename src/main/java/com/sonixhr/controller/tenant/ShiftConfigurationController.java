package com.sonixhr.controller.tenant;


import com.sonixhr.dto.attendance.ShiftConfigurationDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.service.attendance.ShiftConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/shift-configurations")
@RequiredArgsConstructor
public class ShiftConfigurationController {

    private final ShiftConfigurationService shiftConfigurationService;

    // =====================================================
    // CREATE
    // =====================================================

    @PostMapping
    public ResponseEntity<ShiftConfigurationDTO> createShift(@Valid @RequestBody ShiftConfigurationRequestDTO request) {
        Long userId = getCurrentUserId();
        ShiftConfigurationDTO created = shiftConfigurationService.createShiftConfiguration(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);  // ✅ Returns DTO
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @PutMapping("/{id}")
    public ResponseEntity<ShiftConfigurationDTO> updateShift(
            @PathVariable Long id,
            @Valid @RequestBody ShiftConfigurationRequestDTO request) {
        Long userId = getCurrentUserId();
        ShiftConfigurationDTO updated = shiftConfigurationService.updateShiftConfiguration(id, request, userId);
        return ResponseEntity.ok(updated);  // ✅ Returns DTO
    }

    // =====================================================
    // GET METHODS - All return DTO, not Entity
    // =====================================================

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ShiftConfigurationDTO> getShiftByTenant(@PathVariable UUID tenantId) {
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftConfiguration(tenantId);  // ✅ Returns DTO
        return ResponseEntity.ok(shift);  // ✅ Returns DTO
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftConfigurationDTO> getShiftById(@PathVariable Long id) {
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftConfigurationById(id);  // ✅ Returns DTO
        return ResponseEntity.ok(shift);  // ✅ Returns DTO
    }

    @GetMapping("/code/{shiftCode}")
    public ResponseEntity<ShiftConfigurationDTO> getShiftByCode(@PathVariable String shiftCode) {
        ShiftConfigurationDTO shift = shiftConfigurationService.getShiftByCode(shiftCode);  // ✅ Returns DTO
        return ResponseEntity.ok(shift);  // ✅ Returns DTO
    }

    @GetMapping("/tenant/{tenantId}/effective")
    public ResponseEntity<ShiftConfigurationDTO> getEffectiveShift(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ShiftConfigurationDTO shift = shiftConfigurationService.getEffectiveShiftOnDate(tenantId, date);  // ✅ Returns DTO
        return ResponseEntity.ok(shift);  // ✅ Returns DTO
    }

    @GetMapping("/tenant/{tenantId}/all")
    public ResponseEntity<List<ShiftConfigurationDTO>> getAllShifts(@PathVariable UUID tenantId) {
        List<ShiftConfigurationDTO> shifts = shiftConfigurationService.getAllShiftConfigurations(tenantId);  // ✅ Returns List<DTO>
        return ResponseEntity.ok(shifts);  // ✅ Returns List<DTO>
    }

    @GetMapping("/active")
    public ResponseEntity<List<ShiftConfigurationDTO>> getAllActiveShifts() {
        List<ShiftConfigurationDTO> shifts = shiftConfigurationService.getAllActiveShifts();  // ✅ Returns List<DTO>
        return ResponseEntity.ok(shifts);  // ✅ Returns List<DTO>
    }

    // =====================================================
    // UTILITY ENDPOINTS
    // =====================================================

    @GetMapping("/tenant/{tenantId}/validate-checkin")
    public ResponseEntity<Boolean> isValidCheckinTime(
            @PathVariable UUID tenantId,
            @RequestParam LocalTime checkinTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean isValid = shiftConfigurationService.isValidCheckinTime(tenantId, checkinTime, date);
        return ResponseEntity.ok(isValid);
    }

    @GetMapping("/tenant/{tenantId}/late-minutes")
    public ResponseEntity<Integer> calculateLateMinutes(
            @PathVariable UUID tenantId,
            @RequestParam LocalTime checkinTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int lateMinutes = shiftConfigurationService.calculateLateMinutes(tenantId, checkinTime, date);
        return ResponseEntity.ok(lateMinutes);
    }

    @GetMapping("/tenant/{tenantId}/is-weekoff")
    public ResponseEntity<Boolean> isWeekOff(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean isWeekOff = shiftConfigurationService.isWeekOff(tenantId, date);
        return ResponseEntity.ok(isWeekOff);
    }

    @GetMapping("/tenant/{tenantId}/expected-hours")
    public ResponseEntity<Double> getExpectedWorkingHours(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        double hours = shiftConfigurationService.getExpectedWorkingHours(tenantId, date);
        return ResponseEntity.ok(hours);
    }

    // =====================================================
    // ACTIVATE/DEACTIVATE
    // =====================================================

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateShift(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shiftConfigurationService.activateShift(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateShift(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shiftConfigurationService.deactivateShift(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/set-default")
    public ResponseEntity<Void> setDefaultShift(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shiftConfigurationService.setDefaultShift(id, userId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // DELETE
    // =====================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteShift(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        shiftConfigurationService.softDeleteShiftConfiguration(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteShift(@PathVariable Long id) {
        shiftConfigurationService.hardDeleteShiftConfiguration(id);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // HELPER
    // =====================================================

    private Long getCurrentUserId() {
        // TODO: Get from SecurityContextHolder
        // return ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        return 1L; // Placeholder
    }
}