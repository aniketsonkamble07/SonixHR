package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.SupportTicketResponse;
import com.sonixhr.dto.platform.SupportTicketStatusUpdateRequest;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/platform/support-tickets")
@RequiredArgsConstructor
public class PlatformSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_SUPPORT_TICKETS')")
    public ResponseEntity<Page<SupportTicketResponse>> getAllTickets(
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to list support tickets for platform");
        Page<SupportTicketResponse> response = supportTicketService.getAllTickets(tenantName, status, priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_SUPPORT_TICKETS')")
    public ResponseEntity<SupportTicketResponse> getTicketById(@PathVariable Long id) {
        log.info("REST request to view support ticket details: {}", id);
        SupportTicketResponse response = supportTicketService.getTicketById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('MANAGE_SUPPORT_TICKETS', 'RESOLVE_ISSUES')")
    public ResponseEntity<SupportTicketResponse> updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody SupportTicketStatusUpdateRequest request,
            @AuthenticationPrincipal PlatformUser currentUser) {
        log.info("REST request to update support ticket {} status to: {}", id, request.getStatus());
        SupportTicketResponse response = supportTicketService.updateTicketStatus(id, request, currentUser);
        return ResponseEntity.ok(response);
    }
}
