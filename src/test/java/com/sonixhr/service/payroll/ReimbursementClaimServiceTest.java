package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.ReimbursementClaim;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.enums.payroll.ReimbursementCategory;
import com.sonixhr.enums.payroll.ReimbursementStatus;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.ReimbursementClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ReimbursementClaimServiceTest {

    @Mock
    private ReimbursementClaimRepository claimRepo;

    @Mock
    private EmployeeRepository employeeRepo;

    @InjectMocks
    private ReimbursementClaimService claimService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSubmitClaim_Success() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenant(tenant);

        when(employeeRepo.findById(1L)).thenReturn(Optional.of(employee));
        when(claimRepo.save(any(ReimbursementClaim.class))).thenAnswer(i -> i.getArgument(0));

        ReimbursementClaim claim = claimService.submitClaim(
                1L, 10L, BigDecimal.valueOf(500), ReimbursementCategory.TRAVEL, 4, 2026, "url");

        assertNotNull(claim);
        assertEquals(ReimbursementStatus.SUBMITTED, claim.getStatus());
    }

    @Test
    public void testSubmitClaim_CrossTenantIdor_ThrowsBusinessException() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenant(tenant);

        when(employeeRepo.findById(1L)).thenReturn(Optional.of(employee));

        assertThrows(BusinessException.class, () -> {
            claimService.submitClaim(
                    1L, 20L, BigDecimal.valueOf(500), ReimbursementCategory.TRAVEL, 4, 2026, "url");
        });
    }

    @Test
    public void testApproveClaim_Success() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        ReimbursementClaim claim = ReimbursementClaim.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .status(ReimbursementStatus.SUBMITTED)
                .build();

        when(claimRepo.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(claimRepo.save(any(ReimbursementClaim.class))).thenAnswer(i -> i.getArgument(0));

        ReimbursementClaim approved = claimService.approveClaim(claim.getId(), 10L);

        assertNotNull(approved);
        assertEquals(ReimbursementStatus.APPROVED, approved.getStatus());
    }

    @Test
    public void testApproveClaim_CrossTenantIdor_ThrowsBusinessException() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        ReimbursementClaim claim = ReimbursementClaim.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .status(ReimbursementStatus.SUBMITTED)
                .build();

        when(claimRepo.findById(claim.getId())).thenReturn(Optional.of(claim));

        assertThrows(BusinessException.class, () -> {
            claimService.approveClaim(claim.getId(), 20L);
        });
    }

    @Test
    public void testApproveClaim_InvalidTransition_ThrowsBusinessException() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);

        ReimbursementClaim claim = ReimbursementClaim.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .status(ReimbursementStatus.REJECTED)
                .build();

        when(claimRepo.findById(claim.getId())).thenReturn(Optional.of(claim));

        assertThrows(BusinessException.class, () -> {
            claimService.approveClaim(claim.getId(), 10L);
        });
    }
}
