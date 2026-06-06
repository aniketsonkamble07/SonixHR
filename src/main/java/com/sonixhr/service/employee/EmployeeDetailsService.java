package com.sonixhr.service.employee;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service("employeeDetailsService")
@Primary
@RequiredArgsConstructor
public class EmployeeDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Loading employee by email: {}", email);

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Employee not found for email: {}", email);
                    return new UsernameNotFoundException("Employee not found: " + email);
                });

        if (!employee.isActive()) {
            log.warn("Employee account is inactive for email: {}", email);
            throw new UsernameNotFoundException("Employee account is inactive");
        }

        log.info("Employee loaded successfully: {}, tenantId: {}", email, employee.getTenantId());
        return employee;
    }
}