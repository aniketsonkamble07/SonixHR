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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

        // Security check
        boolean hasViewAllPermission = currentEmployee.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_VIEW_ALL") || a.getAuthority().equals("EMPLOYEE_VIEW_TEAM"));

        if (!hasViewAllPermission && !currentEmployee.getId().equals(payslip.getEmployee().getId())) {
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

        // Security check
        boolean hasViewAllPermission = currentEmployee.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_VIEW_ALL") || a.getAuthority().equals("EMPLOYEE_VIEW_TEAM"));

        if (!hasViewAllPermission && !currentEmployee.getId().equals(employeeId)) {
            throw new BusinessException("Access denied: You do not have permission to view these payslips.");
        }

        List<Payslip> payslips = payslipRepo.findByEmployeeId(employeeId);
        return payslips.stream()
                .map(p -> {
                    Payrun payrun = payrunRepo.findById(p.getPayrunId()).orElse(null);
                    return PayslipSummaryResponse.builder()
                            .id(p.getId())
                            .payrunId(p.getPayrunId())
                            .employeeId(p.getEmployee().getId())
                            .employeeCode(p.getEmployee().getEmployeeCode())
                            .fullName(p.getEmployee().getFullName())
                            .month(payrun != null ? payrun.getMonth() : null)
                            .year(payrun != null ? payrun.getYear() : null)
                            .grossEarnings(p.getGrossEarnings())
                            .totalDeductions(p.getTotalDeductions())
                            .netPay(p.getNetPay())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<PayslipSummaryResponse> getTenantPayslips(Long tenantId, Integer month, Integer year, Employee currentEmployee) {
        log.info("Fetching payslips for tenant {} in {}/{}", tenantId, month, year);
        boolean hasViewAllPermission = currentEmployee.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_VIEW_ALL") || a.getAuthority().equals("EMPLOYEE_VIEW_TEAM") || a.getAuthority().equals("SETTINGS_VIEW"));
        if (!hasViewAllPermission) {
            throw new BusinessException("Access denied: You do not have permission to view tenant-wide payslips.");
        }

        List<Payslip> payslips = payslipRepo.findByTenantAndMonthAndYear(tenantId, month, year);
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
