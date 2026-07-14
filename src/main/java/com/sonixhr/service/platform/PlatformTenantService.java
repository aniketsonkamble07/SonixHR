package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformTenantResponseDTO;
import com.sonixhr.dto.platform.TenantPlanOverrideDTO;
import com.sonixhr.util.CountryUtils;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.dto.tenant.TenantUpdateRequest;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.TenantDeletionLogRepository;
import com.sonixhr.entity.platform.TenantDeletionLog;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import com.sonixhr.security.TenantRLSService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class PlatformTenantService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final EmployeeDetailsService employeeDetailsService;
    private final TenantRegistrationService tenantRegistrationService;
    private final TenantRLSService tenantRLSService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final com.sonixhr.service.common.AuditLogService auditLogService;
    private final com.sonixhr.service.common.CacheEvictionService cacheEvictionService;
    private final PlatformUserRepository platformUserRepository;
    private final TenantDeletionLogRepository tenantDeletionLogRepository;
    private final EntityManager entityManager;

    public Page<PlatformTenantResponseDTO> getAllTenants(String companyName, String status, Boolean isActive,
            Pageable pageable) {
        log.info("Fetching all tenants with filter - name: {}, status: {}, isActive: {}", companyName, status,
                isActive);
        Page<Tenant> tenants = tenantRepository.searchTenants(companyName, status, isActive, pageable);
        return tenants.map(this::convertToDTO);
    }

    public PlatformTenantResponseDTO getTenantById(Long id) {
        log.info("Fetching tenant details for ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        return convertToDTO(tenant);
    }

    @Transactional
    public PlatformTenantResponseDTO suspendTenant(Long id, String reason) {
        log.info("Suspending tenant ID: {} for reason: {}", id, reason);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        String oldStatus = tenant.getStatus() != null ? tenant.getStatus().name() : "ACTIVE";
        tenant.suspend(reason);
        Tenant savedTenant = tenantRepository.save(tenant);

        // Suspend their active subscriptions as well
        subscriptionRepository.findByTenantIdAndIsActiveTrue(id).ifPresent(sub -> {
            sub.suspend();
            subscriptionRepository.save(sub);
        });

        // Invalidate employee caches to force logout
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);

        auditLogService.log(
                savedTenant,
                "TENANT_SUSPENDED",
                "status",
                oldStatus,
                savedTenant.getStatus() != null ? savedTenant.getStatus().name() : null,
                "{\"reason\":\"" + reason + "\"}");

        return convertToDTO(savedTenant);
    }

    @Transactional
    public PlatformTenantResponseDTO activateTenant(Long id) {
        log.info("Activating tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        tenant.activate();
        Tenant savedTenant = tenantRepository.save(tenant);

        // Reactivate their subscriptions
        subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(id).stream()
                .findFirst()
                .ifPresent(sub -> {
                    sub.activate();
                    subscriptionRepository.save(sub);
                });

        // Invalidate employee caches to allow re-login
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);

        return convertToDTO(savedTenant);
    }

    @Transactional
    public PlatformTenantResponseDTO overrideTenantPlan(Long id, TenantPlanOverrideDTO dto) {
        log.info("Overriding plan details for tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        SubscriptionPlan plan = subscriptionPlanRepository.findByNameIgnoreCase(dto.getPlanType())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found with code: " + dto.getPlanType()));

        tenant.setSubscriptionPlan(plan);
        tenant.setMaxEmployees(dto.getMaxEmployees());
        tenant.setPlanStatus(PlanStatus.ACTIVE);

        int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
        java.time.LocalDateTime newEndsAt = java.time.LocalDateTime.now().plusMonths(validityMonths);
        tenant.setEndsAt(newEndsAt);
        Tenant savedTenant = tenantRepository.save(tenant);

        // Update current active subscription settings
        TenantSubscription sub = subscriptionRepository.findByTenantIdAndIsActiveTrue(id)
                .orElseGet(() -> TenantSubscription.builder()
                        .tenant(savedTenant)
                        .planName(plan.getName())
                        .currency("INR")
                        .build());

        sub.setSubscriptionPlan(plan);
        sub.setPlanName(plan.getName());
        sub.setIsActive(true);
        sub.setPlanStatus(PlanStatus.ACTIVE);

        if (sub.getStartedAt() == null) {
            sub.setStartedAt(java.time.LocalDateTime.now());
        }
        if (sub.getBillingPeriodStart() == null) {
            sub.setBillingPeriodStart(sub.getStartedAt());
        }
        sub.setEndsAt(newEndsAt);
        sub.setBillingPeriodEnd(newEndsAt);
        if (sub.getAmount() == null) {
            sub.setAmount(plan.getPrice());
        }
        subscriptionRepository.save(sub);

        // Invalidate employee caches
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);

        return convertToDTO(savedTenant);
    }

    @Transactional
    public PlatformTenantResponseDTO createTenant(TenantRegistrationRequest request) {
        log.info("Creating tenant from platform layer: {}", request.getCompanyName());
        TenantRegistrationResponse registrationResponse = tenantRegistrationService.registerTenant(request);
        Tenant tenant = tenantRepository.findById(registrationResponse.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found after creation"));
        return convertToDTO(tenant);
    }

    @Transactional
    public PlatformTenantResponseDTO updateTenant(Long id, TenantUpdateRequest request) {
        log.info("Updating tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (request.getCompanyName() != null) {
            tenant.setCompanyName(request.getCompanyName());
        }
        if (request.getAdminEmail() != null) {
            tenant.setAdminEmail(request.getAdminEmail());
        }
        if (request.getAdminName() != null) {
            tenant.setAdminName(request.getAdminName());
        }
        if (request.getAdminPhone() != null) {
            tenant.setAdminPhone(request.getAdminPhone());
        }
        if (request.getOfficeAddress() != null) {
            tenant.setOfficeAddress(request.getOfficeAddress());
        }
        if (request.getCity() != null) {
            tenant.setCity(request.getCity());
        }
        if (request.getState() != null) {
            tenant.setState(request.getState());
        }
        if (request.getStateText() != null) {
            tenant.setStateText(request.getStateText());
        }
        if (request.getCountry() != null) {
            tenant.setCountry(CountryUtils.normalizeAndValidateCountry(request.getCountry()));
        }

        // Apply country-specific validation and cleanup
        if ("IN".equalsIgnoreCase(tenant.getCountry())) {
            if (tenant.getState() == null) {
                throw new com.sonixhr.exceptions.ValidationException("state", "State is required for tenants in India");
            }
            tenant.setStateText(null);
        } else {
            tenant.setState(null);
        }

        Tenant savedTenant = tenantRepository.save(tenant);

        // Invalidate tenant configuration cache
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);

        return convertToDTO(savedTenant);
    }

    @Transactional
    public PlatformTenantResponseDTO deactivateTenant(Long id) {
        log.info("Deactivating tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        tenant.deactivate();
        Tenant savedTenant = tenantRepository.save(tenant);

        // Deactivate active subscriptions as well
        subscriptionRepository.findByTenantIdAndIsActiveTrue(id).ifPresent(sub -> {
            sub.setIsActive(false);
            subscriptionRepository.save(sub);
        });

        // Invalidate employee caches to force logout
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);

        return convertToDTO(savedTenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        log.info("Deleting tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // 1. Gather data counts for manifest proof
        long employeeCount = countEntityRows("Employee", id);
        long departmentCount = countEntityRows("Department", id);
        long attendanceCount = countEntityRows("AttendanceRecord", id);
        long leaveCount = countEntityRows("LeaveRequest", id);
        long payrunCount = countEntityRows("Payrun", id);
        long payslipCount = countEntityRows("Payslip", id);

        // 2. Generate JSON manifest
        LocalDateTime now = LocalDateTime.now();
        String manifestJson = String.format(
                "{\"tenantId\":%d,\"tenantCode\":%s,\"companyName\":%s,\"deletedAt\":\"%s\"," +
                        "\"employeesCount\":%d,\"departmentsCount\":%d,\"attendanceRecordsCount\":%d," +
                        "\"leaveRequestsCount\":%d,\"payrunsCount\":%d,\"payslipsCount\":%d}",
                id,
                escapeJson(tenant.getTenantCode()),
                escapeJson(tenant.getCompanyName()),
                now.toString(),
                employeeCount,
                departmentCount,
                attendanceCount,
                leaveCount,
                payrunCount,
                payslipCount);

        // 3. Compute SHA-256 Hash
        String manifestHash = calculateSha256(manifestJson);

        // 4. Retrieve current performing administrator
        Long adminId = auditLogService.getCurrentUserId();
        String adminEmail = "System/Unknown Admin";
        if (adminId != null) {
            adminEmail = platformUserRepository.findById(adminId)
                    .map(PlatformUser::getEmail)
                    .orElse("Unknown Admin");
        }

        // 5. Create compliance audit log
        TenantDeletionLog deletionLog = TenantDeletionLog.builder()
                .tenantId(id)
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .deletedAt(now)
                .deletedByAdminId(adminId)
                .deletedByAdminEmail(adminEmail)
                .dataManifestHash(manifestHash)
                .dataManifestJson(manifestJson)
                .build();

        tenantDeletionLogRepository.save(deletionLog);

        // 6. Permanently erase all tenant data across all child tables
        permanentlyEraseTenantData(id);

        // 7. Delete the tenant row itself
        tenantRepository.delete(tenant);

        // Invalidate employee caches and tenant config cache
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
        cacheEvictionService.evictTenantCaches(id);
    }

    @Transactional
    public void permanentlyEraseTenantData(Long tenantId) {
        log.info("Permanently erasing all data for tenant ID: {}", tenantId);

        // Delete from join tables first
        entityManager.createNativeQuery(
                "DELETE FROM employee_roles WHERE employee_id IN (SELECT id FROM employees WHERE tenant_id = :tenantId)")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createNativeQuery(
                "DELETE FROM role_tenant_permissions WHERE role_id IN (SELECT id FROM tenant_roles WHERE tenant_id = :tenantId)")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM PayslipItem x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM Payslip x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM FnfSettlementItem x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM FnfSettlement x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM EmployeeSalaryComponent x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM EmployeeSalaryProfile x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM LoanAdvance x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM ReimbursementClaim x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM LeaveRequest x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM AttendanceRecord x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM EmployeeTask x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM EmployeeAddress x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM EmployeeBankAccount x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        // Remove manager self-references in Employee
        entityManager.createQuery("UPDATE Employee x SET x.manager = null WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM Employee x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM Department x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM SalaryComponentDefinition x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantSalaryStructure x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantPayrollConfig x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM TenantLeaveSettings x WHERE x.tenantId = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM PublicHoliday x WHERE x.tenantId = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM ShiftConfiguration x WHERE x.tenantId = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM TenantSetupToken x WHERE x.tenantId = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantRole x WHERE x.tenantId = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        entityManager.createQuery("DELETE FROM TenantUsageStat x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantSetting x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantFeature x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantBranding x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantBillingInfo x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantAuditLog x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM SupportTicket x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();
        entityManager.createQuery("DELETE FROM TenantSubscription x WHERE x.tenant.id = :tenantId")
                .setParameter("tenantId", tenantId).executeUpdate();

        log.info("Finished deleting all entities for tenant ID: {}", tenantId);
    }

    private long countEntityRows(String entityName, Long tenantId) {
        try {
            return entityManager.createQuery(
                    "SELECT COUNT(x) FROM " + entityName + " x WHERE x.tenant.id = :tenantId", Long.class)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
        } catch (Exception e) {
            log.warn("Failed to count rows for entity {}: {}", entityName, e.getMessage());
            return 0;
        }
    }

    private String calculateSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256 hash", e);
        }
    }

    private String escapeJson(String input) {
        if (input == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"')
                sb.append("\\\"");
            else if (c == '\\')
                sb.append("\\\\");
            else if (c == '\n')
                sb.append("\\n");
            else if (c == '\r')
                sb.append("\\r");
            else if (c == '\t')
                sb.append("\\t");
            else if (c < 32)
                sb.append(String.format("\\u%04x", (int) c));
            else
                sb.append(c);
        }
        sb.append("\"");
        return sb.toString();
    }

    private PlatformTenantResponseDTO convertToDTO(Tenant tenant) {
        if (tenant == null)
            return null;
        return PlatformTenantResponseDTO.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .companyName(tenant.getCompanyName())
                .adminName(tenant.getAdminName())
                .adminEmail(tenant.getAdminEmail())
                .adminPhone(tenant.getAdminPhone())
                .officeAddress(tenant.getOfficeAddress())
                .city(tenant.getCity())
                .state(tenant.getState())
                .stateText(tenant.getStateText())
                .country(tenant.getCountry())
                .planType(tenant.getPlanType())
                .status(tenant.getStatus())
                .isActive(tenant.getIsActive())
                .maxEmployees(tenant.getMaxEmployees())
                .planStatus(tenant.getPlanStatus() != null ? tenant.getPlanStatus().name() : null)
                .endsAt(tenant.getEndsAt())
                .suspendedAt(tenant.getSuspendedAt())
                .suspensionReason(tenant.getSuspensionReason())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .dataStatus(tenant.getDataStatus() != null ? tenant.getDataStatus().name() : null)
                .expiredAt(tenant.getExpiredAt())
                .archivedAt(tenant.getArchivedAt())
                .deletedAt(tenant.getDeletedAt())
                .deletedByAdminId(tenant.getDeletedByAdminId())
                .build();
    }
}
