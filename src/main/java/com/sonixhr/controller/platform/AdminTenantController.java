package com.sonixhr.controller.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.platform.PlatformTenantResponseDTO;
import com.sonixhr.dto.platform.RestoreHistoryResponseDTO;
import com.sonixhr.dto.platform.TenantRestoreRequest;
import com.sonixhr.dto.tenant.TenantSubscriptionResponseDTO;
import com.sonixhr.entity.tenant.TenantAuditLog;
import com.sonixhr.repository.tenant.TenantAuditLogRepository;
import com.sonixhr.service.tenant.TenantSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class AdminTenantController {

    private final TenantSubscriptionService subscriptionService;
    private final TenantAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('TENANT_RESTORE')")
    public ResponseEntity<TenantSubscriptionResponseDTO> restoreTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantRestoreRequest request) {
        log.info("REST admin request to restore tenant ID: {} with plan ID: {}", id, request.getPlanId());
        TenantSubscriptionResponseDTO response = subscriptionService.restoreArchivedTenant(id, request.getPlanId(), request.getNotes());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/restore-history")
    @PreAuthorize("hasAuthority('TENANT_RESTORE')")
    public ResponseEntity<List<RestoreHistoryResponseDTO>> getRestoreHistory(@PathVariable Long id) {
        log.info("REST admin request to get restore history for tenant ID: {}", id);
        Page<TenantAuditLog> logs = auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(id, "TENANT_RESTORE", Pageable.unpaged());
        List<RestoreHistoryResponseDTO> dtos = new ArrayList<>();
        
        for (TenantAuditLog auditLog : logs.getContent()) {
            String notes = "";
            String planName = "";
            String performedByEmail = "Unknown Admin";

            String metadataStr = auditLog.getMetadata();
            if (metadataStr != null && !metadataStr.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(metadataStr);
                    if (node.has("notes")) {
                        notes = node.get("notes").asText();
                    }
                    if (node.has("planName")) {
                        planName = node.get("planName").asText();
                    }
                    if (node.has("performedByEmail")) {
                        performedByEmail = node.get("performedByEmail").asText();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse audit log metadata JSON for ID {}: {}", auditLog.getId(), e.getMessage());
                }
            }

            dtos.add(RestoreHistoryResponseDTO.builder()
                    .id(auditLog.getId())
                    .oldValue(auditLog.getOldValue())
                    .newValue(auditLog.getNewValue())
                    .createdAt(auditLog.getCreatedAt())
                    .performedByEmail(performedByEmail)
                    .notes(notes)
                    .planName(planName)
                    .build());
        }

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}/subscription-history")
    @PreAuthorize("hasAuthority('VIEW_SUBSCRIPTIONS') or hasAuthority('TENANT_RESTORE')")
    public ResponseEntity<List<TenantSubscriptionResponseDTO>> getSubscriptionHistory(@PathVariable Long id) {
        log.info("REST admin request to get subscription history for tenant ID: {}", id);
        List<TenantSubscriptionResponseDTO> history = subscriptionService.getSubscriptionHistory(id);
        return ResponseEntity.ok(history);
    }
}
