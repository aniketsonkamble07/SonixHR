// service/subscription/PlatformSubscriptionPlanService.java
package com.sonixhr.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.subscription.PlanOperationLogDTO;
import com.sonixhr.dto.subscription.SubscriptionPlanDTO;
import com.sonixhr.entity.platform.PlanFeature;
import com.sonixhr.entity.platform.SubscriptionPlan;
import com.sonixhr.entity.tenant.SubscriptionHistory;
import com.sonixhr.enums.PlanStatus;
import com.sonixhr.enums.TriggerSource;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.SubscriptionPlanRepository;
import com.sonixhr.repository.tenant.SubscriptionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PlatformSubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    // =====================================================
    // GET OPERATIONS
    // =====================================================

    @Cacheable(value = "subscriptionPlans", key = "'all'")
    public List<SubscriptionPlanDTO> getAllPlans() {
        return planRepository.findAllActivePlans().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SubscriptionPlanDTO> getPublicPlans() {
        return planRepository.findPublicPlans().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "subscriptionPlans", key = "#id")
    public SubscriptionPlanDTO getPlanById(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + id));
        if (plan.isDeleted()) {
            throw new ResourceNotFoundException("Plan not found with ID: " + id);
        }
        return convertToDTO(plan);
    }

    @Cacheable(value = "subscriptionPlans", key = "#code")
    public SubscriptionPlanDTO getPlanByCode(String code) {
        SubscriptionPlan plan = planRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with code: " + code));
        if (plan.isDeleted()) {
            throw new ResourceNotFoundException("Plan not found with code: " + code);
        }
        return convertToDTO(plan);
    }

    public List<SubscriptionPlanDTO> getActivePlans() {
        return planRepository.findActivePlansOrderByPrice().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SubscriptionPlanDTO> getDeletedPlans() {
        // Since we don't have a direct method for deleted plans, we need to find all and filter
        return planRepository.findAll().stream()
                .filter(SubscriptionPlan::isDeleted)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<PlanOperationLogDTO> getPlanOperationHistory(Long planId) {
        List<SubscriptionHistory> historyList = historyRepository.findAll();

        return historyList.stream()
                .filter(h -> h.getPlanId() != null && h.getPlanId().equals(planId))
                .filter(h -> h.getEventType() != null && h.getEventType().startsWith("PLAN_"))
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .map(this::convertToPlanLogDTO)
                .collect(Collectors.toList());
    }

    public List<PlanOperationLogDTO> getAllPlanOperations() {
        List<SubscriptionHistory> historyList = historyRepository.findAll();

        return historyList.stream()
                .filter(h -> h.getEventType() != null && h.getEventType().startsWith("PLAN_"))
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .map(this::convertToPlanLogDTO)
                .collect(Collectors.toList());
    }

    public boolean isPlanCodeAvailable(String code) {
        return !planRepository.existsByCodeIgnoreCase(code);
    }

    // =====================================================
    // STATISTICS OPERATIONS
    // =====================================================

    public Map<String, Object> getPlanStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<SubscriptionPlan> allPlans = planRepository.findAll();
        List<SubscriptionPlan> activePlans = allPlans.stream()
                .filter(p -> p.isActive() && !p.isDeleted())
                .collect(Collectors.toList());
        List<SubscriptionPlan> deletedPlans = allPlans.stream()
                .filter(SubscriptionPlan::isDeleted)
                .collect(Collectors.toList());

        stats.put("totalPlans", allPlans.size());
        stats.put("activePlans", activePlans.size());
        stats.put("deletedPlans", deletedPlans.size());
        stats.put("publicPlans", allPlans.stream().filter(p -> Boolean.TRUE.equals(p.getIsPublic()) && !p.isDeleted()).count());
        stats.put("customPlans", allPlans.stream().filter(p -> Boolean.TRUE.equals(p.getIsCustom()) && !p.isDeleted()).count());
        stats.put("freePlans", allPlans.stream().filter(p -> p.isFree() && !p.isDeleted()).count());

        // Average price
        OptionalDouble avgPrice = activePlans.stream()
                .map(SubscriptionPlan::getPrice)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        stats.put("averagePrice", avgPrice.orElse(0.0));

        // Min and max price
        Optional<BigDecimal> minPrice = activePlans.stream()
                .map(SubscriptionPlan::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo);
        Optional<BigDecimal> maxPrice = activePlans.stream()
                .map(SubscriptionPlan::getPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo);
        stats.put("minPrice", minPrice.orElse(BigDecimal.ZERO));
        stats.put("maxPrice", maxPrice.orElse(BigDecimal.ZERO));

        return stats;
    }

    public Map<String, Object> getPlanUsageStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<SubscriptionHistory> historyList = historyRepository.findAll();

        // Plan operation counts
        long planCreated = historyList.stream()
                .filter(h -> "PLAN_CREATED".equals(h.getEventType()))
                .count();
        long planUpdated = historyList.stream()
                .filter(h -> "PLAN_UPDATED".equals(h.getEventType()))
                .count();
        long planDeleted = historyList.stream()
                .filter(h -> "PLAN_DELETED".equals(h.getEventType()))
                .count();
        long planRestored = historyList.stream()
                .filter(h -> "PLAN_RESTORED".equals(h.getEventType()))
                .count();
        long planToggled = historyList.stream()
                .filter(h -> "PLAN_TOGGLED".equals(h.getEventType()))
                .count();

        stats.put("planCreated", planCreated);
        stats.put("planUpdated", planUpdated);
        stats.put("planDeleted", planDeleted);
        stats.put("planRestored", planRestored);
        stats.put("planToggled", planToggled);
        stats.put("totalPlanOperations", planCreated + planUpdated + planDeleted + planRestored + planToggled);

        // Most active plans
        Map<String, Long> planActivity = historyList.stream()
                .filter(h -> h.getPlanCode() != null)
                .collect(Collectors.groupingBy(
                        SubscriptionHistory::getPlanCode,
                        Collectors.counting()
                ));

        stats.put("planActivity", planActivity);
        stats.put("mostActivePlan", planActivity.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN"));

        return stats;
    }

    // =====================================================
    // CREATE OPERATIONS
    // =====================================================

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO createPlan(SubscriptionPlanDTO dto) {
        validatePlanUniqueness(dto.getName(), dto.getCode(), null);

        SubscriptionPlan plan = convertToEntity(dto);
        SubscriptionPlan saved = planRepository.save(plan);

        // Log plan creation
        logPlanOperation(saved, null, "PLAN_CREATED", buildCreationLog(saved));

        log.info("Created plan: {} ({})", saved.getName(), saved.getCode());
        return convertToDTO(saved);
    }

    // =====================================================
    // UPDATE OPERATIONS
    // =====================================================

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO updatePlan(Long id, SubscriptionPlanDTO dto) {
        SubscriptionPlan existing = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + id));

        if (existing.isDeleted()) {
            throw new ResourceNotFoundException("Plan not found with ID: " + id);
        }

        validatePlanUniqueness(dto.getName(), dto.getCode(), id);

        // Track changes before update
        SubscriptionPlan oldState = copyPlan(existing);
        Map<String, Object> changes = trackPlanChanges(existing, dto);

        // Update plan
        updatePlanFields(existing, dto);
        updatePlanFeatures(existing, dto.getFeatures());

        SubscriptionPlan updated = planRepository.save(existing);

        // Log plan update with changes
        if (!changes.isEmpty()) {
            logPlanOperation(updated, oldState, "PLAN_UPDATED", changes);
        }

        log.info("Updated plan: {} ({})", updated.getName(), updated.getCode());
        return convertToDTO(updated);
    }

    // =====================================================
    // DELETE OPERATIONS
    // =====================================================

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public void deletePlan(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + id));

        if (plan.isDeleted()) {
            throw new BusinessException("Plan is already deleted");
        }

        // Store old state for logging
        SubscriptionPlan oldState = copyPlan(plan);

        plan.softDelete();
        planRepository.save(plan);

        // Log deletion
        Map<String, Object> changes = new HashMap<>();
        changes.put("deletedAt", LocalDateTime.now());
        changes.put("wasActive", oldState.isActive());
        logPlanOperation(plan, oldState, "PLAN_DELETED", changes);

        log.info("Deleted plan: {} ({})", plan.getName(), plan.getCode());
    }

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO restorePlan(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + id));

        if (!plan.isDeleted()) {
            throw new BusinessException("Plan is not deleted, cannot restore");
        }

        SubscriptionPlan oldState = copyPlan(plan);

        plan.restore();
        SubscriptionPlan restored = planRepository.save(plan);

        Map<String, Object> changes = new HashMap<>();
        changes.put("restoredAt", LocalDateTime.now());
        changes.put("newActiveState", restored.isActive());
        logPlanOperation(restored, oldState, "PLAN_RESTORED", changes);

        log.info("Restored plan: {} ({})", restored.getName(), restored.getCode());
        return convertToDTO(restored);
    }

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO togglePlanActive(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + id));

        if (plan.isDeleted()) {
            throw new BusinessException("Cannot toggle active status of a deleted plan");
        }

        Boolean oldActive = plan.isActive();
        SubscriptionPlan oldState = copyPlan(plan);
        plan.setIsActive(!plan.isActive());
        SubscriptionPlan saved = planRepository.save(plan);

        Map<String, Object> changes = new HashMap<>();
        changes.put("fieldChanged", "isActive");
        changes.put("oldValue", oldActive);
        changes.put("newValue", saved.isActive());
        logPlanOperation(saved, oldState, "PLAN_TOGGLED", changes);

        log.info("Toggled active status for plan: {} to {}", saved.getName(), saved.isActive());
        return convertToDTO(saved);
    }

    // =====================================================
    // FEATURE MANAGEMENT OPERATIONS
    // =====================================================

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO addFeatureToPlan(Long planId, String featureCode, String description) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));

        if (plan.isDeleted()) {
            throw new BusinessException("Cannot add feature to a deleted plan");
        }

        // Check if feature already exists
        boolean exists = plan.getPlanFeatures().stream()
                .anyMatch(f -> f.getFeatureCode().equalsIgnoreCase(featureCode));

        if (exists) {
            throw new BusinessException("Feature '" + featureCode + "' already exists in this plan");
        }

        PlanFeature feature = PlanFeature.builder()
                .subscriptionPlan(plan)
                .featureCode(featureCode)
                .description(description)
                .isEnabled(true)
                .build();

        plan.getPlanFeatures().add(feature);
        SubscriptionPlan saved = planRepository.save(plan);

        // Log the feature addition as a plan update
        Map<String, Object> changes = new HashMap<>();
        changes.put("fieldChanged", "features");
        changes.put("featureAdded", featureCode);
        logPlanOperation(saved, null, "PLAN_UPDATED", changes);

        log.info("Added feature {} to plan: {}", featureCode, plan.getName());
        return convertToDTO(saved);
    }

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public SubscriptionPlanDTO removeFeatureFromPlan(Long planId, String featureCode) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));

        if (plan.isDeleted()) {
            throw new BusinessException("Cannot remove feature from a deleted plan");
        }

        boolean removed = plan.getPlanFeatures().removeIf(f -> f.getFeatureCode().equalsIgnoreCase(featureCode));

        if (!removed) {
            throw new BusinessException("Feature '" + featureCode + "' not found in this plan");
        }

        SubscriptionPlan saved = planRepository.save(plan);

        // Log the feature removal as a plan update
        Map<String, Object> changes = new HashMap<>();
        changes.put("fieldChanged", "features");
        changes.put("featureRemoved", featureCode);
        logPlanOperation(saved, null, "PLAN_UPDATED", changes);

        log.info("Removed feature {} from plan: {}", featureCode, plan.getName());
        return convertToDTO(saved);
    }

    // =====================================================
    // BULK OPERATIONS
    // =====================================================

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public List<SubscriptionPlanDTO> bulkCreatePlans(List<SubscriptionPlanDTO> planDTOs) {
        List<SubscriptionPlanDTO> createdPlans = new ArrayList<>();

        for (SubscriptionPlanDTO dto : planDTOs) {
            try {
                SubscriptionPlanDTO created = createPlan(dto);
                createdPlans.add(created);
            } catch (Exception e) {
                log.error("Failed to create plan {}: {}", dto.getCode(), e.getMessage());
                throw new BusinessException("Failed to create plan: " + dto.getCode() + " - " + e.getMessage());
            }
        }

        log.info("Bulk created {} plans", createdPlans.size());
        return createdPlans;
    }

    @Transactional
    @CacheEvict(value = "subscriptionPlans", allEntries = true)
    public void bulkDeletePlans(List<Long> planIds) {
        for (Long planId : planIds) {
            try {
                deletePlan(planId);
            } catch (Exception e) {
                log.error("Failed to delete plan {}: {}", planId, e.getMessage());
                throw new BusinessException("Failed to delete plan with ID: " + planId + " - " + e.getMessage());
            }
        }
        log.info("Bulk deleted {} plans", planIds.size());
    }

    // =====================================================
    // PRIVATE LOGGING METHODS
    // =====================================================

    private void logPlanOperation(SubscriptionPlan current, SubscriptionPlan previous,
                                  String eventType, Map<String, Object> changes) {
        try {
            SubscriptionHistory history = buildHistoryEntity(current, previous, eventType, changes);
            historyRepository.save(history);
            log.info("Plan operation logged: {} for plan: {} ({})",
                    eventType, current.getCode(), current.getName());
        } catch (Exception e) {
            log.error("Failed to log plan operation: {}", e.getMessage(), e);
        }
    }

    private SubscriptionHistory buildHistoryEntity(SubscriptionPlan current, SubscriptionPlan previous,
                                                   String eventType, Map<String, Object> changes) {
        SubscriptionHistory history = new SubscriptionHistory();
        history.setTenantId(1L); // System tenant
        history.setPlanId(current.getId());
        history.setPlanCode(current.getCode());
        history.setPlanName(current.getName());
        history.setPlanType(current.getCode());
        history.setEventType(eventType);
        history.setEventDate(LocalDateTime.now());
        history.setAmount(current.getPrice());
        history.setCurrency(current.getCurrency());
        history.setPlanStatus(current.isActive() ? PlanStatus.ACTIVE : PlanStatus.CANCELLED);

        if (previous != null) {
            history.setPreviousPlanId(previous.getId());
            history.setPreviousPlanCode(previous.getCode());
            history.setPreviousPlanName(previous.getName());
            history.setPreviousPrice(previous.getPrice());
            history.setPreviousMaxEmployees(previous.getMaxEmployees());
            history.setPreviousValidityMonths(previous.getValidityMonths());
            history.setPreviousStatus(previous.isActive() ? "ACTIVE" : "CANCELLED");
            history.setPreviousPlanType(previous.getCode());
        }

        history.setNewPrice(current.getPrice());
        history.setNewMaxEmployees(current.getMaxEmployees());
        history.setNewValidityMonths(current.getValidityMonths());
        history.setNewStatus(current.isActive() ? "ACTIVE" : "CANCELLED");
        history.setNewPlanType(current.getCode());

        if (changes.containsKey("fieldChanged")) {
            history.setFieldChanged(changes.get("fieldChanged").toString());
        }
        if (changes.containsKey("oldValue")) {
            history.setOldValue(changes.get("oldValue").toString());
        }
        if (changes.containsKey("newValue")) {
            history.setNewValue(changes.get("newValue").toString());
        }

        history.setTriggerSource(TriggerSource.ADMIN);
        history.setCreatedBy(getCurrentUser());
        history.setMetadata(convertChangesToJson(changes));

        return history;
    }

    private String convertChangesToJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            return changes.toString();
        }
    }

    private String getCurrentUser() {
        return "SYSTEM";
    }

    // =====================================================
    // CHANGE TRACKING METHODS
    // =====================================================

    private Map<String, Object> trackPlanChanges(SubscriptionPlan existing, SubscriptionPlanDTO dto) {
        Map<String, Object> changes = new LinkedHashMap<>();
        List<String> fieldsChanged = new ArrayList<>();
        List<String> oldValues = new ArrayList<>();
        List<String> newValues = new ArrayList<>();

        if (!existing.getName().equals(dto.getName())) {
            fieldsChanged.add("name");
            oldValues.add(existing.getName());
            newValues.add(dto.getName());
        }

        if (!existing.getCode().equals(dto.getCode())) {
            fieldsChanged.add("code");
            oldValues.add(existing.getCode());
            newValues.add(dto.getCode());
        }

        if (existing.getPrice() == null || dto.getPrice() == null || existing.getPrice().compareTo(dto.getPrice()) != 0) {
            fieldsChanged.add("price");
            oldValues.add(existing.getPrice() != null ? existing.getPrice().toString() : "null");
            newValues.add(dto.getPrice() != null ? dto.getPrice().toString() : "null");
        }

        if (!Objects.equals(existing.getDescription(), dto.getDescription())) {
            fieldsChanged.add("description");
            oldValues.add(existing.getDescription() != null ? existing.getDescription() : "null");
            newValues.add(dto.getDescription() != null ? dto.getDescription() : "null");
        }

        if (!Objects.equals(existing.getMaxEmployees(), dto.getMaxEmployees())) {
            fieldsChanged.add("maxEmployees");
            oldValues.add(existing.getMaxEmployees() != null ? existing.getMaxEmployees().toString() : "null");
            newValues.add(dto.getMaxEmployees() != null ? dto.getMaxEmployees().toString() : "null");
        }

        if (!Objects.equals(existing.getValidityMonths(), dto.getValidityMonths())) {
            fieldsChanged.add("validityMonths");
            oldValues.add(existing.getValidityMonths() != null ? existing.getValidityMonths().toString() : "null");
            newValues.add(String.valueOf(dto.getValidityMonths()));
        }

        if (!Objects.equals(existing.getCurrency(), dto.getCurrency())) {
            fieldsChanged.add("currency");
            oldValues.add(existing.getCurrency() != null ? existing.getCurrency() : "null");
            newValues.add(dto.getCurrency() != null ? dto.getCurrency() : "null");
        }

        if (!Objects.equals(existing.isActive(), dto.isActive())) {
            fieldsChanged.add("isActive");
            oldValues.add(String.valueOf(existing.isActive()));
            newValues.add(String.valueOf(dto.isActive()));
        }

        if (!Objects.equals(existing.getIsPublic(), dto.isPublic())) {
            fieldsChanged.add("isPublic");
            oldValues.add(existing.getIsPublic() != null ? existing.getIsPublic().toString() : "null");
            newValues.add(String.valueOf(dto.isPublic()));
        }

        // Track feature changes
        Set<String> existingFeatures = existing.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureCode)
                .collect(Collectors.toSet());
        Set<String> newFeatures = dto.getFeatures() != null ? dto.getFeatures() : new HashSet<>();

        if (!existingFeatures.equals(newFeatures)) {
            Set<String> added = new HashSet<>(newFeatures);
            added.removeAll(existingFeatures);

            Set<String> removed = new HashSet<>(existingFeatures);
            removed.removeAll(newFeatures);

            if (!added.isEmpty() || !removed.isEmpty()) {
                fieldsChanged.add("features");
                oldValues.add("[" + String.join(", ", existingFeatures) + "]");
                newValues.add("[" + String.join(", ", newFeatures) + "]");
                changes.put("featuresAdded", added);
                changes.put("featuresRemoved", removed);
            }
        }

        if (!fieldsChanged.isEmpty()) {
            changes.put("fieldChanged", String.join(",", fieldsChanged));
            changes.put("oldValue", String.join(", ", oldValues));
            changes.put("newValue", String.join(", ", newValues));
        }

        return changes;
    }

    // =====================================================
    // CONVERSION METHODS
    // =====================================================

    private SubscriptionPlanDTO convertToDTO(SubscriptionPlan plan) {
        if (plan == null) return null;

        Set<String> features = new HashSet<>();
        if (plan.getPlanFeatures() != null) {
            features = plan.getPlanFeatures().stream()
                    .filter(PlanFeature::isEnabled)
                    .map(PlanFeature::getFeatureCode)
                    .collect(Collectors.toSet());
        }

        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setId(plan.getId());
        dto.setCode(plan.getCode());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setPrice(plan.getPrice());
        dto.setCurrency(plan.getCurrency());
        dto.setValidityMonths(plan.getValidityMonths() != null ? plan.getValidityMonths() : 1);
        dto.setMaxUsers(plan.getMaxUsers());
        dto.setMaxEmployees(plan.getMaxEmployees());
        dto.setActive(plan.isActive());
        dto.setPublic(plan.getIsPublic() != null && plan.getIsPublic());
        dto.setDisplayOrder(plan.getDisplayOrder() != null ? plan.getDisplayOrder() : 0);
        dto.setCustom(plan.getIsCustom() != null && plan.getIsCustom());
        dto.setFeatures(features);
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        dto.setDeletedAt(plan.getDeletedAt());

        return dto;
    }

    private SubscriptionPlan convertToEntity(SubscriptionPlanDTO dto) {
        if (dto == null) return null;

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(dto.getCode());
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setPrice(dto.getPrice());
        plan.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
        plan.setValidityMonths(dto.getValidityMonths());
        plan.setMaxUsers(dto.getMaxUsers());
        plan.setMaxEmployees(dto.getMaxEmployees());
        plan.setIsActive(dto.isActive());
        plan.setIsPublic(dto.isPublic());
        plan.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        plan.setIsCustom(dto.isCustom());
        plan.setPlanFeatures(new HashSet<>());

        if (dto.getFeatures() != null && !dto.getFeatures().isEmpty()) {
            for (String featureCode : dto.getFeatures()) {
                PlanFeature feature = PlanFeature.builder()
                        .subscriptionPlan(plan)
                        .featureCode(featureCode)
                        .isEnabled(true)
                        .build();
                plan.getPlanFeatures().add(feature);
            }
        }

        return plan;
    }

    private PlanOperationLogDTO convertToPlanLogDTO(SubscriptionHistory history) {
        PlanOperationLogDTO dto = new PlanOperationLogDTO();
        dto.setId(history.getId());
        dto.setTenantId(history.getTenantId());
        dto.setPlanId(history.getPlanId());
        dto.setPlanCode(history.getPlanCode());
        dto.setPlanName(history.getPlanName());
        dto.setEventType(history.getEventType());
        dto.setEventDate(history.getEventDate());
        dto.setAmount(history.getAmount());
        dto.setCurrency(history.getCurrency());
        dto.setPreviousPlanId(history.getPreviousPlanId());
        dto.setPreviousPlanCode(history.getPreviousPlanCode());
        dto.setPreviousPlanName(history.getPreviousPlanName());
        dto.setPreviousPrice(history.getPreviousPrice());
        dto.setNewPrice(history.getNewPrice());
        dto.setPreviousMaxEmployees(history.getPreviousMaxEmployees());
        dto.setNewMaxEmployees(history.getNewMaxEmployees());
        dto.setPreviousValidityMonths(history.getPreviousValidityMonths());
        dto.setNewValidityMonths(history.getNewValidityMonths());
        dto.setFieldChanged(history.getFieldChanged());
        dto.setOldValue(history.getOldValue());
        dto.setNewValue(history.getNewValue());
        dto.setTriggerSource(history.getTriggerSource() != null ?
                history.getTriggerSource().name() : null);
        dto.setTriggeredById(history.getTriggeredById());
        dto.setCreatedBy(history.getCreatedBy());
        dto.setCreatedAt(history.getCreatedAt());
        return dto;
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private void validatePlanUniqueness(String name, String code, Long excludeId) {
        if (excludeId == null) {
            if (planRepository.existsByNameIgnoreCase(name)) {
                throw new BusinessException("Plan with name '" + name + "' already exists");
            }
            if (planRepository.existsByCodeIgnoreCase(code)) {
                throw new BusinessException("Plan with code '" + code + "' already exists");
            }
        } else {
            // For updates, check if the name/code is used by another plan
            planRepository.findByNameIgnoreCase(name)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(excludeId)) {
                            throw new BusinessException("Plan with name '" + name + "' already exists");
                        }
                    });
            planRepository.findByCodeIgnoreCase(code)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(excludeId)) {
                            throw new BusinessException("Plan with code '" + code + "' already exists");
                        }
                    });
        }
    }

    private void updatePlanFields(SubscriptionPlan plan, SubscriptionPlanDTO dto) {
        plan.setCode(dto.getCode());
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setPrice(dto.getPrice());
        plan.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
        plan.setValidityMonths(dto.getValidityMonths());
        plan.setMaxUsers(dto.getMaxUsers());
        plan.setMaxEmployees(dto.getMaxEmployees());
        plan.setIsActive(dto.isActive());
        plan.setIsPublic(dto.isPublic());
        plan.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        plan.setIsCustom(dto.isCustom());
    }

    private void updatePlanFeatures(SubscriptionPlan plan, Set<String> newFeatureCodes) {
        if (plan.getPlanFeatures() == null) {
            plan.setPlanFeatures(new HashSet<>());
        }

        if (newFeatureCodes == null || newFeatureCodes.isEmpty()) {
            plan.getPlanFeatures().clear();
            return;
        }

        // 1. Remove features that are no longer present
        java.util.Iterator<PlanFeature> iterator = plan.getPlanFeatures().iterator();
        while (iterator.hasNext()) {
            PlanFeature feature = iterator.next();
            if (!newFeatureCodes.contains(feature.getFeatureCode())) {
                iterator.remove();
            }
        }

        // 2. Collect existing feature codes to avoid duplicates
        Set<String> existingFeatureCodes = new HashSet<>();
        for (PlanFeature feature : plan.getPlanFeatures()) {
            existingFeatureCodes.add(feature.getFeatureCode());
        }

        // 3. Add new features
        for (String featureCode : newFeatureCodes) {
            if (!existingFeatureCodes.contains(featureCode)) {
                PlanFeature feature = PlanFeature.builder()
                        .subscriptionPlan(plan)
                        .featureCode(featureCode)
                        .isEnabled(true)
                        .build();
                plan.getPlanFeatures().add(feature);
            }
        }
    }

    private SubscriptionPlan copyPlan(SubscriptionPlan plan) {
        SubscriptionPlan copy = new SubscriptionPlan();
        copy.setId(plan.getId());
        copy.setCode(plan.getCode());
        copy.setName(plan.getName());
        copy.setDescription(plan.getDescription());
        copy.setPrice(plan.getPrice());
        copy.setCurrency(plan.getCurrency());
        copy.setValidityMonths(plan.getValidityMonths());
        copy.setMaxUsers(plan.getMaxUsers());
        copy.setMaxEmployees(plan.getMaxEmployees());
        copy.setIsActive(plan.isActive());
        copy.setIsPublic(plan.getIsPublic());
        copy.setDisplayOrder(plan.getDisplayOrder());
        copy.setIsCustom(plan.getIsCustom());
        copy.setDeletedAt(plan.getDeletedAt());
        return copy;
    }

    private Map<String, Object> buildCreationLog(SubscriptionPlan plan) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("planId", plan.getId());
        log.put("planCode", plan.getCode());
        log.put("planName", plan.getName());
        log.put("price", plan.getPrice());
        log.put("currency", plan.getCurrency());
        log.put("validityMonths", plan.getValidityMonths());
        log.put("maxEmployees", plan.getMaxEmployees());
        log.put("isActive", plan.isActive());
        log.put("isPublic", plan.getIsPublic());
        log.put("features", plan.getEnabledFeatureCodes());
        return log;
    }
}