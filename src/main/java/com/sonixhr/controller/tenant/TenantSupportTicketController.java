package com.sonixhr.controller.tenant;

import com.sonixhr.dto.platform.SupportTicketCreateRequest;
import com.sonixhr.dto.platform.SupportTicketResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.service.platform.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
@RestController
@RequestMapping("/api/employee/support-tickets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TenantSupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> createTicket(
            @Valid @RequestBody SupportTicketCreateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to raise support ticket for employee: {}", currentEmployee.getId());
        SupportTicketResponse response = supportTicketService.createTicket(request, currentEmployee);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<SupportTicketResponse>> getMyTenantTickets(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to list support tickets for tenant: {}", currentEmployee.getTenantId());
        Page<SupportTicketResponse> response = supportTicketService.getTenantTickets(
                currentEmployee.getTenantId(), status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getTicketById(
            @PathVariable Long id,
            @AuthenticationPrincipal Employee currentEmployee) {
        log.info("REST request to get support ticket {} for tenant {}", id, currentEmployee.getTenantId());
        SupportTicketResponse response = supportTicketService.getTenantTicketById(id, currentEmployee.getTenantId());
        return ResponseEntity.ok(response);
    }
}
