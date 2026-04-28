package com.sonixhr.dto;

import com.sonixhr.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public Employee toEntity(EmployeeCreateRequest request) {
        return Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .position(request.getPosition())
                .hireDate(request.getHireDate())
                .customFields(request.getCustomFields())
                .build();
    }

    public void updateEntity(Employee employee, EmployeeUpdateRequest request) {
        if (request.getFirstName() != null) {
            employee.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            employee.setLastName(request.getLastName());
        }
        if (request.getEmail() != null) {
            employee.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            employee.setPhone(request.getPhone());
        }
        if (request.getDepartment() != null) {
            employee.setDepartment(request.getDepartment());
        }
        if (request.getPosition() != null) {
            employee.setPosition(request.getPosition());
        }
        if (request.getCustomFields() != null) {
            employee.setCustomFields(request.getCustomFields());
        }
    }

    public EmployeeResponse toResponse(Employee employee) {
        if (employee == null) return null;

        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFirstName() + " " + employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .department(employee.getDepartment())
                .position(employee.getPosition())
                .status(employee.getStatus() != null ? employee.getStatus().name() : null)
                .hireDate(employee.getHireDate())
                .resignationDate(employee.getResignationDate())
                .customFields(employee.getCustomFields())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .manager(employee.getManager() != null ?
                        EmployeeResponse.ManagerInfo.builder()
                                .id(employee.getManager().getId())
                                .fullName(employee.getManager().getFirstName() + " " + employee.getManager().getLastName())
                                .email(employee.getManager().getEmail())
                                .build() : null)
                .build();
    }
}