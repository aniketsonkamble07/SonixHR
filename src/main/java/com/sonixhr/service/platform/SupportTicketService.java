package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.SupportTicketCreateRequest;
import com.sonixhr.dto.platform.SupportTicketResponse;
import com.sonixhr.dto.platform.SupportTicketStatusUpdateRequest;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.platform.SupportTicket;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Transactional(readOnly = true)
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final PlatformNotificationServiceExt notificationService;
    private static final Random RANDOM = new Random();

    /**
     * Raise a support ticket (Tenant Employee)
     */
    @Transactional
    public SupportTicketResponse createTicket(SupportTicketCreateRequest request, Employee currentEmployee) {
        log.info("Creating support ticket for tenant: {} by employee: {}", currentEmployee.getTenantId(), currentEmployee.getId());

        String ticketNumber = generateTicketNumber();

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNumber)
                .tenant(currentEmployee.getTenant())
                .raisedBy(currentEmployee)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(request.getPriority())
                .status("OPEN")
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);

        // Notify platform team
        String alertTitle = String.format("New Support Ticket raised: %s", ticketNumber);
        String alertMsg = String.format("Ticket raised by %s (%s). Category: %s, Priority: %s. Title: %s",
                currentEmployee.getFullName(),
                currentEmployee.getTenant().getCompanyName(),
                request.getCategory(),
                request.getPriority(),
                request.getTitle());

        notificationService.notifyPlatformTeam(
                alertTitle,
                alertMsg,
                "TICKET_CREATED",
                ticketNumber,
                currentEmployee.getTenant().getCompanyName(),
                "OPEN",
                "CREATED"
        );

        return toResponse(saved);
    }

    /**
     * Retrieve tickets for a specific tenant (with optional status filtering)
     */
    public Page<SupportTicketResponse> getTenantTickets(Long tenantId, String status, Pageable pageable) {
        log.info("Fetching support tickets for tenant: {} - Filter status: {}", tenantId, status);
        String normalizedStatus = (status == null || status.trim().isEmpty()) ? null : status.trim();
        return supportTicketRepository.searchTenantTickets(tenantId, normalizedStatus, pageable)
                .map(this::toResponse);
    }

    /**
     * Get a specific support ticket by ID (verifying tenant ownership)
     */
    public SupportTicketResponse getTenantTicketById(Long id, Long tenantId) {
        log.info("Fetching support ticket: {} for tenant: {}", id, tenantId);
        SupportTicket ticket = supportTicketRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found"));
        return toResponse(ticket);
    }

    /**
     * Retrieve all tickets for the platform team (with optional filtering)
     */
    public Page<SupportTicketResponse> getAllTickets(String tenantName, String status, String priority, Pageable pageable) {
        log.info("Fetching all support tickets for platform. Filters: tenant={}, status={}, priority={}", tenantName, status, priority);
        String normTenant = (tenantName == null || tenantName.trim().isEmpty()) ? null : tenantName.trim();
        String normStatus = (status == null || status.trim().isEmpty()) ? null : status.trim();
        String normPriority = (priority == null || priority.trim().isEmpty()) ? null : priority.trim();

        return supportTicketRepository.searchTickets(normTenant, normStatus, normPriority, pageable)
                .map(this::toResponse);
    }

    /**
     * Get ticket by ID for platform team
     */
    public SupportTicketResponse getTicketById(Long id) {
        log.info("Fetching support ticket: {} for platform team", id);
        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found"));
        return toResponse(ticket);
    }

    /**
     * Update ticket status and resolution (Platform User)
     */
    @Transactional
    public SupportTicketResponse updateTicketStatus(Long id, SupportTicketStatusUpdateRequest request, PlatformUser currentUser) {
        log.info("Updating support ticket: {} to status: {} by platform user: {}", id, request.getStatus(), currentUser.getId());

        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found"));

        ticket.setStatus(request.getStatus());
        if ("RESOLVED".equalsIgnoreCase(request.getStatus()) || "CLOSED".equalsIgnoreCase(request.getStatus())) {
            ticket.setResolvedBy(currentUser);
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setResolution(request.getResolution());
        }

        SupportTicket saved = supportTicketRepository.save(ticket);

        // Notify platform team of updates
        String alertTitle = String.format("Support Ticket Status Updated: %s", ticket.getTicketNumber());
        String alertMsg = String.format("Ticket status updated to %s by %s. Resolution: %s",
                request.getStatus(),
                currentUser.getFullName(),
                request.getResolution() != null ? request.getResolution() : "N/A");

        notificationService.notifyPlatformTeam(
                alertTitle,
                alertMsg,
                "TICKET_UPDATED",
                ticket.getTicketNumber(),
                ticket.getTenant().getCompanyName(),
                request.getStatus(),
                "UPDATED"
        );

        return toResponse(saved);
    }

    private String generateTicketNumber() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = 1000 + RANDOM.nextInt(9000); // 4-digit random number
        return "TKT-" + dateStr + "-" + rand;
    }

    private SupportTicketResponse toResponse(SupportTicket ticket) {
        if (ticket == null) return null;
        return SupportTicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .tenantId(ticket.getTenant() != null ? ticket.getTenant().getId() : null)
                .tenantCompanyName(ticket.getTenant() != null ? ticket.getTenant().getCompanyName() : null)
                .tenantCode(ticket.getTenant() != null ? ticket.getTenant().getTenantCode() : null)
                .raisedByEmployeeId(ticket.getRaisedBy() != null ? ticket.getRaisedBy().getId() : null)
                .raisedByEmployeeName(ticket.getRaisedBy() != null ? ticket.getRaisedBy().getFullName() : null)
                .raisedByEmployeeEmail(ticket.getRaisedBy() != null ? ticket.getRaisedBy().getEmail() : null)
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .resolution(ticket.getResolution())
                .resolvedByPlatformUserId(ticket.getResolvedBy() != null ? ticket.getResolvedBy().getId() : null)
                .resolvedByPlatformUserName(ticket.getResolvedBy() != null ? ticket.getResolvedBy().getFullName() : null)
                .resolvedAt(ticket.getResolvedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
