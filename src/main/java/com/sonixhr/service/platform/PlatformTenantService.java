package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformTenantResponseDTO;
import com.sonixhr.dto.platform.TenantPlanOverrideDTO;
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
import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.tenant.TenantRegistrationService;
import com.sonixhr.security.TenantRLSService;
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

    public Page<PlatformTenantResponseDTO> getAllTenants(String companyName, String status, Boolean isActive, Pageable pageable) {
        log.info("Fetching all tenants with filter - name: {}, status: {}, isActive: {}", companyName, status, isActive);
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

        auditLogService.log(
            savedTenant,
            "TENANT_SUSPENDED",
            "status",
            oldStatus,
            savedTenant.getStatus() != null ? savedTenant.getStatus().name() : null,
            "{\"reason\":\"" + reason + "\"}"
        );

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

        return convertToDTO(savedTenant);
    }

    @Transactional
    public PlatformTenantResponseDTO overrideTenantPlan(Long id, TenantPlanOverrideDTO dto) {
        log.info("Overriding plan details for tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeIgnoreCase(dto.getPlanType())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found with code: " + dto.getPlanType()));

        tenant.setSubscriptionPlan(plan);
        tenant.setMaxEmployees(dto.getMaxEmployees());
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        if (tenant.getEndsAt() == null) {
            int validityMonths = plan.getValidityMonths() > 0 ? plan.getValidityMonths() : 1;
            tenant.setEndsAt(java.time.LocalDateTime.now().plusMonths(validityMonths));
        }
        Tenant savedTenant = tenantRepository.save(tenant);

        // Update current active subscription settings
        TenantSubscription sub = subscriptionRepository.findByTenantIdAndIsActiveTrue(id)
                .orElseGet(() -> TenantSubscription.builder()
                        .tenant(savedTenant)
                        .planName(plan.getName())
                        .currency("INR")
                        .billingCycle(com.sonixhr.enums.BillingCycle.MONTHLY)
                        .build());
        
        sub.setSubscriptionPlan(plan);
        sub.setPlanName(plan.getName());
        sub.setMaxEmployees(dto.getMaxEmployees());
        sub.setIsActive(true);
        sub.setMaxStorageMb(dto.getMaxStorageMb());
        sub.setPlanStatus(PlanStatus.ACTIVE);
        
        if (sub.getStartedAt() == null) {
            sub.setStartedAt(java.time.LocalDateTime.now());
        }
        if (sub.getEndsAt() == null) {
            sub.setEndsAt(tenant.getEndsAt());
        }
        if (sub.getAmount() == null) {
            sub.setAmount(java.math.BigDecimal.valueOf(plan.getMonthlyPrice()));
        }
        subscriptionRepository.save(sub);

        // Invalidate employee caches
        employeeDetailsService.invalidateAllCaches();

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
        if (request.getCountry() != null) {
            tenant.setCountry(request.getCountry());
        }

        Tenant savedTenant = tenantRepository.save(tenant);
        
        // Invalidate tenant configuration cache
        tenantRLSService.invalidateTenantCache(id);
        
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

        return convertToDTO(savedTenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        log.info("Deleting tenant ID: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        tenant.softDelete();
        tenantRepository.save(tenant);

        // Deactivate active subscriptions as well
        subscriptionRepository.findByTenantIdAndIsActiveTrue(id).ifPresent(sub -> {
            sub.setIsActive(false);
            subscriptionRepository.save(sub);
        });

        // Invalidate employee caches and tenant config cache
        employeeDetailsService.invalidateAllCaches();
        tenantRLSService.invalidateTenantCache(id);
    }

    private PlatformTenantResponseDTO convertToDTO(Tenant tenant) {
        if (tenant == null) return null;
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
                .build();
    }
}
