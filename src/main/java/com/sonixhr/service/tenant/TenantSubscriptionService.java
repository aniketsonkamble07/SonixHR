package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantSubscription;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.enums.BillingCycle;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.tenant.TenantSubscriptionRepository;
import com.sonixhr.security.TenantRLSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class TenantSubscriptionService {

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantRLSService tenantRLSService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public TenantSubscriptionResponseDTO currentSubscription(Long tenantId) {
        log.info("Fetching current active subscription for tenant ID: {}", tenantId);
        TenantSubscription subscription = subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant"));
        return convertToDTO(subscription);
    }

    @Transactional
    public TenantSubscriptionResponseDTO renewSubscription(Long tenantId) {
        log.info("Renewing subscription for tenant ID: {}", tenantId);
        TenantSubscription currentSub = subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant"));

        // Extend endsAt date by plan validity months, adjusting for yearly billing cycle
        int validityMonths = currentSub.getSubscriptionPlan() != null && currentSub.getSubscriptionPlan().getValidityMonths() > 0 
                ? currentSub.getSubscriptionPlan().getValidityMonths() : 1;
        long monthsToAdd = currentSub.getBillingCycle() == BillingCycle.YEARLY ? 12L : validityMonths;
        LocalDateTime newEndsAt = currentSub.getEndsAt() != null ? currentSub.getEndsAt().plusMonths(monthsToAdd) : LocalDateTime.now().plusMonths(monthsToAdd);
        currentSub.setEndsAt(newEndsAt);
        currentSub.setPlanStatus(PlanStatus.ACTIVE);
        currentSub.setIsActive(true);

        TenantSubscription savedSub = subscriptionRepository.save(currentSub);
        
        tenantRLSService.invalidateTenantCache(tenantId);
        return convertToDTO(savedSub);
    }

    @Transactional
    public TenantSubscriptionResponseDTO upgradeSubscription(Long tenantId, String planTypeCode, String billingCycleStr) {
        log.info("Changing subscription for tenant ID: {} to plan: {} (billing cycle: {})", tenantId, planTypeCode, billingCycleStr);
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        SubscriptionPlan newPlan = subscriptionPlanRepository.findByCodeIgnoreCase(planTypeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found: " + planTypeCode));
        if (!newPlan.isActive()) {
            throw new BusinessException("Cannot change to a deactivated subscription plan: " + planTypeCode);
        }
        BillingCycle billingCycle = BillingCycle.valueOf(billingCycleStr.toUpperCase());

        // Validate limits if downgrading
        long employeeCount = employeeRepository.countByTenantId(tenantId);
        if (newPlan.getMaxEmployees() > 0 && employeeCount > newPlan.getMaxEmployees()) {
            throw new BusinessException("Cannot change plan: current employee count (" + employeeCount 
                    + ") exceeds the new plan limit (" + newPlan.getMaxEmployees() + ")");
        }

        // Deactivate existing active subscription
        subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId).ifPresent(sub -> {
            sub.setIsActive(false);
            subscriptionRepository.save(sub);
        });

        // Update tenant settings
        tenant.setSubscriptionPlan(newPlan);
        tenant.setMaxEmployees(newPlan.getMaxEmployees());
        tenant.setPlanStatus(PlanStatus.ACTIVE);
        tenantRepository.save(tenant);

        int validityMonths = newPlan.getValidityMonths() > 0 ? newPlan.getValidityMonths() : 1;
        long monthsToAdd = billingCycle == BillingCycle.YEARLY ? 12L : validityMonths;
        LocalDateTime endsAt = LocalDateTime.now().plusMonths(monthsToAdd);

        // Determine price: 10% discount for yearly billing cycle
        double price = billingCycle == BillingCycle.YEARLY ? newPlan.getMonthlyPrice() * 12 * 0.9 : newPlan.getMonthlyPrice() * validityMonths;

        // Create a new subscription record
        TenantSubscription newSub = TenantSubscription.builder()
                .tenant(tenant)
                .subscriptionPlan(newPlan)
                .planName(newPlan.getName())
                .planStatus(PlanStatus.ACTIVE)
                .maxEmployees(newPlan.getMaxEmployees())
                .maxStorageMb(newPlan.getMaxStorageMb())
                .startedAt(LocalDateTime.now())
                .endsAt(endsAt)
                .amount(BigDecimal.valueOf(price))
                .currency("INR")
                .billingCycle(billingCycle)
                .isActive(true)
                .build();

        TenantSubscription savedSub = subscriptionRepository.save(newSub);
        
        tenantRLSService.invalidateTenantCache(tenantId);
        return convertToDTO(savedSub);
    }

    @Transactional
    public TenantSubscriptionResponseDTO cancelSubscription(Long tenantId) {
        log.info("Cancelling subscription for tenant ID: {}", tenantId);
        TenantSubscription currentSub = subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found for tenant"));

        currentSub.cancel();
        TenantSubscription savedSub = subscriptionRepository.save(currentSub);

        Tenant tenant = currentSub.getTenant();
        if (tenant != null) {
            tenant.setPlanStatus(PlanStatus.CANCELLED);
            tenantRepository.save(tenant);
        }
        
        tenantRLSService.invalidateTenantCache(tenantId);
        return convertToDTO(savedSub);
    }

    public List<TenantSubscriptionResponseDTO> getSubscriptionHistory(Long tenantId) {
        log.info("Fetching subscription history for tenant ID: {}", tenantId);
        List<TenantSubscription> list = subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private TenantSubscriptionResponseDTO convertToDTO(TenantSubscription sub) {
        if (sub == null) return null;
        return TenantSubscriptionResponseDTO.builder()
                .id(sub.getId())
                .planType(sub.getPlanType())
                .planName(sub.getPlanName())
                .planStatus(sub.getPlanStatus())
                .maxEmployees(sub.getMaxEmployees() != null ? sub.getMaxEmployees() : 0)
                .maxStorageMb(sub.getMaxStorageMb() != null ? sub.getMaxStorageMb() : 0)
                .startedAt(sub.getStartedAt())
                .endsAt(sub.getEndsAt())
                .amount(sub.getAmount())
                .currency(sub.getCurrency())
                .billingCycle(sub.getBillingCycle())
                .isActive(sub.getIsActive() != null ? sub.getIsActive() : false)
                .createdAt(sub.getCreatedAt())
                .build();
    }
}
