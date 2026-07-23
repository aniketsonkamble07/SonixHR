package com.sonixhr.controller.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.platform.TenantRestoreHistoryResponse;
import com.sonixhr.dto.subscription.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantAuditLog;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.repository.tenant.TenantAuditLogRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.subscription.TenantSubscriptionService;
import com.sonixhr.service.platform.PlatformTenantService;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.Payslip;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Admin Tenants", description = "Platform Admin APIs for organization tenant restoration and subscription history")
@SecurityRequirement(name = "bearerAuth")
public class PlatformAdminTenantController {

    private final TenantSubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTenantService platformTenantService;
    private final EmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TenantRestoreRequest {
        private Long planId;
        private String notes;
    }

    @PostMapping("/{tenantId}/restore")
    @PreAuthorize("hasAuthority('MANAGE_TENANTS')")
    @Operation(summary = "Restore tenant organization", description = "Restores a soft-deleted or expired tenant and assigns a subscription plan.")
    public ResponseEntity<TenantSubscriptionResponseDTO> restoreTenant(
            @PathVariable Long tenantId,
            @RequestBody(required = false) TenantRestoreRequest request) {
        log.info("Platform admin request to restore tenant: {}", tenantId);

        Long planId = request != null ? request.getPlanId() : null;
        String notes = request != null ? request.getNotes() : null;

        // Auto-resolve planId if not provided
        if (planId == null) {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                if (tenant.getSubscriptionPlan() != null) {
                    planId = tenant.getSubscriptionPlan().getId();
                } else {
                    planId = subscriptionPlanRepository.findAllActivePlans().stream()
                            .findFirst()
                            .map(SubscriptionPlan::getId)
                            .orElse(null);
                }
            }
        }

        if (planId == null) {
            throw new BusinessException("Target plan ID could not be resolved automatically. Please provide planId.");
        }

        if (notes == null || notes.trim().isEmpty()) {
            notes = "Restored by Platform Admin";
        }

        TenantSubscriptionResponseDTO response = subscriptionService.restoreArchivedTenant(tenantId, planId, notes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tenantId}/subscription-history")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS')")
    @Operation(summary = "Get tenant subscription history", description = "Retrieves a list of all historical subscription plans for a specific tenant.")
    public ResponseEntity<List<TenantSubscriptionResponseDTO>> getTenantSubscriptionHistory(
            @PathVariable Long tenantId) {
        log.info("Platform admin request to get subscription history for tenant: {}", tenantId);
        return ResponseEntity.ok(subscriptionService.getSubscriptionHistory(tenantId));
    }

    @GetMapping("/{tenantId}/restore-history")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS')")
    @Operation(summary = "Get tenant restoration history", description = "Retrieves restoration audit event logs for a specific tenant.")
    public ResponseEntity<List<TenantRestoreHistoryResponse>> getTenantRestoreHistory(
            @PathVariable Long tenantId) {
        log.info("Platform admin request to get restore history for tenant: {}", tenantId);

        // Fetch up to 100 restore logs
        List<TenantAuditLog> logs = auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(
                tenantId, "TENANT_RESTORE", PageRequest.of(0, 100)
        ).getContent();

        List<TenantRestoreHistoryResponse> result = new ArrayList<>();
        for (TenantAuditLog logEntry : logs) {
            result.add(convertToRestoreHistoryResponse(logEntry));
        }

        return ResponseEntity.ok(result);
    }

    private TenantRestoreHistoryResponse convertToRestoreHistoryResponse(TenantAuditLog auditLog) {
        String performedByEmail = "Unknown Admin";
        String notes = "";
        String planName = "";

        if (auditLog.getMetadata() != null) {
            try {
                Map<String, Object> metadata = objectMapper.readValue(auditLog.getMetadata(), Map.class);
                if (metadata.containsKey("performedByEmail") && metadata.get("performedByEmail") != null) {
                    performedByEmail = String.valueOf(metadata.get("performedByEmail"));
                }
                if (metadata.containsKey("notes") && metadata.get("notes") != null) {
                    notes = String.valueOf(metadata.get("notes"));
                }
                if (metadata.containsKey("planName") && metadata.get("planName") != null) {
                    planName = String.valueOf(metadata.get("planName"));
                }
            } catch (Exception e) {
                log.error("Failed to parse tenant restore audit log metadata", e);
            }
        }

        return TenantRestoreHistoryResponse.builder()
                .id(auditLog.getId())
                .oldValue(auditLog.getOldValue())
                .newValue(auditLog.getNewValue())
                .createdAt(auditLog.getCreatedAt())
                .performedByEmail(performedByEmail)
                .notes(notes)
                .planName(planName)
                .build();
    }

    @DeleteMapping("/{tenantId}/purge")
    @PreAuthorize("hasAuthority('MANAGE_TENANTS')")
    @Operation(summary = "Permanently purge tenant data", description = "Manually triggers the hard purge of all organization child data while preserving the tenant metadata row.")
    public ResponseEntity<Void> purgeTenant(@PathVariable Long tenantId) {
        log.info("Platform admin manual request to hard purge tenant: {}", tenantId);
        platformTenantService.deleteTenant(tenantId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tenantId}/export/employees")
    @PreAuthorize("hasAuthority('MANAGE_TENANTS')")
    @Operation(summary = "Export tenant employees data (Admin reference)", description = "Allows the platform team to export employee data for reference/restoration within the 1-year cycle.")
    public ResponseEntity<byte[]> exportTenantEmployees(@PathVariable Long tenantId) {
        log.info("Platform admin manual request to export employees for tenant: {}", tenantId);
        List<Employee> employees = employeeRepository.findByTenant_Id(tenantId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Employee Code,First Name,Last Name,Email,Status,Department,Manager,Hire Date\n");

        for (Employee emp : employees) {
            csv.append(emp.getId()).append(",")
               .append(escapeCsv(emp.getEmployeeCode())).append(",")
               .append(escapeCsv(emp.getFirstName())).append(",")
               .append(escapeCsv(emp.getLastName())).append(",")
               .append(escapeCsv(emp.getEmail())).append(",")
               .append(emp.getStatus() != null ? emp.getStatus().name() : "").append(",")
               .append(emp.getDepartment() != null ? escapeCsv(emp.getDepartment().getName()) : "").append(",")
               .append(emp.getManager() != null ? escapeCsv(emp.getManager().getEmail()) : "").append(",")
               .append(emp.getHireDate() != null ? emp.getHireDate().toString() : "")
               .append("\n");
        }

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employees-export-" + tenantId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/{tenantId}/export/payroll")
    @PreAuthorize("hasAuthority('MANAGE_TENANTS')")
    @Operation(summary = "Export tenant payroll data (Admin reference)", description = "Allows the platform team to export payroll data for reference/restoration within the 1-year cycle.")
    public ResponseEntity<byte[]> exportTenantPayroll(@PathVariable Long tenantId) {
        log.info("Platform admin manual request to export payroll for tenant: {}", tenantId);
        List<Payslip> payslips = payslipRepository.findByTenant_Id(tenantId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Employee Code,Employee Email,Payrun ID,Gross Earnings,Total Deductions,Net Pay,Wages Base\n");

        for (Payslip slip : payslips) {
            csv.append(slip.getId()).append(",")
               .append(slip.getEmployee() != null ? escapeCsv(slip.getEmployee().getEmployeeCode()) : "").append(",")
               .append(slip.getEmployee() != null ? escapeCsv(slip.getEmployee().getEmail()) : "").append(",")
               .append(slip.getPayrunId()).append(",")
               .append(slip.getGrossEarnings()).append(",")
               .append(slip.getTotalDeductions()).append(",")
               .append(slip.getNetPay()).append(",")
               .append(slip.getWagesBase())
               .append("\n");
        }

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payroll-export-" + tenantId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
