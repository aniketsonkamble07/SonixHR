package com.sonixhr.service.payroll;

import com.sonixhr.dto.payroll.PayslipResponse;
import com.sonixhr.dto.payroll.PayslipSummaryResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.Payslip;
import com.sonixhr.entity.payroll.PayslipItem;
import com.sonixhr.entity.payroll.Payrun;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.PayslipItemRepository;
import com.sonixhr.repository.payroll.PayslipRepository;
import com.sonixhr.repository.payroll.PayrunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
// Service for managing employee payslips and salary slips
public class PayslipService {

    private final PayslipRepository payslipRepo;
    private final PayslipItemRepository payslipItemRepo;
    private final EmployeeRepository employeeRepository;
    private final PayrunRepository payrunRepo;

    public PayslipResponse getPayslip(Long tenantId, UUID payslipId, Employee currentEmployee) {
        log.info("Fetching payslip {} for tenant {}", payslipId, tenantId);
        Payslip payslip = payslipRepo.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found with id: " + payslipId));

        if (!payslip.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Access denied: Payslip does not belong to this tenant");
        }

        // Fix 2: EMPLOYEE_VIEW_ALL grants access to all payslips.
        // EMPLOYEE_VIEW_TEAM grants access only to direct reports' payslips (manager check).
        // All others can only view their own payslip.
        boolean hasViewAll     = currentEmployee.hasPermission("EMPLOYEE_VIEW_ALL");
        boolean isOwnPayslip   = currentEmployee.getId().equals(payslip.getEmployee().getId());
        boolean isDirectReport = currentEmployee.hasPermission("EMPLOYEE_VIEW_TEAM")
                && payslip.getEmployee().getManager() != null
                && payslip.getEmployee().getManager().getId().equals(currentEmployee.getId());

        if (!hasViewAll && !isDirectReport && !isOwnPayslip) {
            throw new BusinessException("Access denied: You do not have permission to view this payslip.");
        }

        Payrun payrun = payrunRepo.findById(payslip.getPayrunId())
                .orElseThrow(() -> new ResourceNotFoundException("Payrun not found with id: " + payslip.getPayrunId()));

        List<PayslipItem> items = payslipItemRepo.findByPayslipId(payslip.getId());
        List<PayslipResponse.PayslipItemDto> itemDtos = items.stream()
                .map(i -> PayslipResponse.PayslipItemDto.builder()
                        .id(i.getId())
                        .componentCode(i.getComponentCode())
                        .componentName(i.getComponentName())
                        .type(i.getType())
                        .amount(i.getAmount())
                        .expressionUsed(i.getExpressionUsed())
                        .build())
                .collect(Collectors.toList());

        return PayslipResponse.builder()
                .id(payslip.getId())
                .payrunId(payslip.getPayrunId())
                .employeeId(payslip.getEmployee().getId())
                .employeeCode(payslip.getEmployee().getEmployeeCode())
                .fullName(payslip.getEmployee().getFullName())
                .departmentName(payslip.getEmployee().getDepartment() != null ? payslip.getEmployee().getDepartment().getName() : null)
                .designation(payslip.getEmployee().getPosition())
                .month(payrun.getMonth())
                .year(payrun.getYear())
                .grossEarnings(payslip.getGrossEarnings())
                .totalDeductions(payslip.getTotalDeductions())
                .netPay(payslip.getNetPay())
                .lopDays(payslip.getLopDays())
                .wagesBase(payslip.getWagesBase())
                .items(itemDtos)
                .build();
    }

    public List<PayslipSummaryResponse> getEmployeePayslips(Long tenantId, Long employeeId, Employee currentEmployee) {
        log.info("Fetching payslips for employee {} in tenant {}", employeeId, tenantId);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (!employee.getTenantId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }

        // Fix 2: EMPLOYEE_VIEW_TEAM can only view direct reports, not any employee.
        boolean hasViewAll     = currentEmployee.hasPermission("EMPLOYEE_VIEW_ALL");
        boolean isOwnRequest   = currentEmployee.getId().equals(employeeId);
        boolean isDirectReport = currentEmployee.hasPermission("EMPLOYEE_VIEW_TEAM")
                && employee.getManager() != null
                && employee.getManager().getId().equals(currentEmployee.getId());

        if (!hasViewAll && !isDirectReport && !isOwnRequest) {
            throw new BusinessException("Access denied: You do not have permission to view these payslips.");
        }

        List<Payslip> payslips = payslipRepo.findByEmployeeId(tenantId, employeeId);

        // Fix 1: batch-fetch all payruns in one query instead of N individual queries.
        List<UUID> payrunIds = payslips.stream().map(Payslip::getPayrunId).collect(Collectors.toList());
        Map<UUID, Payrun> payrunMap = payrunRepo.findAllById(payrunIds).stream()
                .collect(Collectors.toMap(Payrun::getId, r -> r));

        return payslips.stream()
                .map(p -> {
                    // Fix 4: throw instead of silently producing null month/year in the response.
                    Payrun payrun = payrunMap.get(p.getPayrunId());
                    if (payrun == null) {
                        throw new ResourceNotFoundException(
                                "Payrun not found for payslip " + p.getId() + " — data integrity issue");
                    }
                    return PayslipSummaryResponse.builder()
                            .id(p.getId())
                            .payrunId(p.getPayrunId())
                            .employeeId(p.getEmployee().getId())
                            .employeeCode(p.getEmployee().getEmployeeCode())
                            .fullName(p.getEmployee().getFullName())
                            .month(payrun.getMonth())
                            .year(payrun.getYear())
                            .grossEarnings(p.getGrossEarnings())
                            .totalDeductions(p.getTotalDeductions())
                            .netPay(p.getNetPay())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<PayslipSummaryResponse> getTenantPayslips(
            Long tenantId, Integer month, Integer year, Employee currentEmployee, int page, int size) {
        log.info("Fetching payslips for tenant {} in {}/{}", tenantId, month, year);

        // Fix 5: validate month and year before hitting the DB.
        if (month == null || year == null) {
            throw new BusinessException("VALIDATION_ERROR", "Month and year are required");
        }
        if (month < 1 || month > 12) {
            throw new BusinessException("VALIDATION_ERROR", "Month must be between 1 and 12");
        }

        // Fix 2: separate EMPLOYEE_VIEW_ALL/SETTINGS_VIEW (full access) from EMPLOYEE_VIEW_TEAM
        // (direct reports only). Previously EMPLOYEE_VIEW_TEAM was treated identically to EMPLOYEE_VIEW_ALL.
        boolean hasViewAll  = currentEmployee.hasPermission("EMPLOYEE_VIEW_ALL") || currentEmployee.hasPermission("SETTINGS_VIEW");
        boolean hasViewTeam = currentEmployee.hasPermission("EMPLOYEE_VIEW_TEAM");

        if (!hasViewAll && !hasViewTeam) {
            throw new BusinessException("Access denied: You do not have permission to view tenant-wide payslips.");
        }

        List<Payslip> payslips;
        if (hasViewAll) {
            // Fix 3: paginate for admin / SETTINGS_VIEW access to avoid full-table load.
            payslips = payslipRepo.findByTenantAndMonthAndYearPaged(
                    tenantId, month, year,
                    PageRequest.of(page, size, Sort.by("id").ascending())
            ).getContent();
        } else {
            // EMPLOYEE_VIEW_TEAM: fetch all and filter to direct reports in memory.
            // Team sizes are bounded so in-memory filtering is acceptable.
            Long managerId = currentEmployee.getId();
            payslips = payslipRepo.findByTenantAndMonthAndYear(tenantId, month, year).stream()
                    .filter(p -> p.getEmployee().getManager() != null
                            && p.getEmployee().getManager().getId().equals(managerId))
                    .collect(Collectors.toList());
        }

        return payslips.stream()
                .map(p -> PayslipSummaryResponse.builder()
                        .id(p.getId())
                        .payrunId(p.getPayrunId())
                        .employeeId(p.getEmployee().getId())
                        .employeeCode(p.getEmployee().getEmployeeCode())
                        .fullName(p.getEmployee().getFullName())
                        .month(month)
                        .year(year)
                        .grossEarnings(p.getGrossEarnings())
                        .totalDeductions(p.getTotalDeductions())
                        .netPay(p.getNetPay())
                        .build())
                .collect(Collectors.toList());
    }
}
