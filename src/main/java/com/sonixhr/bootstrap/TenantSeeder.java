package com.sonixhr.bootstrap;

import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.service.tenant.TenantRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class TenantSeeder implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final TenantRegistrationService registrationService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Tenant Seeder Started");
        log.info("=========================================");

        if (tenantRepository.count() == 0) {
            log.info("No tenants found in the database. Seeding a default tenant...");
            try {
                TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                        .companyName("Acme Corporation")
                        .subdomain("acme")
                        .adminEmail("admin@acme.com")
                        .adminName("Acme Admin")
                        .adminPhone("9876543210")
                        .planType("basic")
                        .build();

                registrationService.registerTenant(request);
                log.info("Default tenant 'Acme Corporation' seeded successfully.");
            } catch (Exception e) {
                log.error("Failed to seed default tenant", e);
            }
        } else {
            log.info("Tenants already exist in the database. Skipping seeding.");
        }

        log.info("=========================================");
        log.info("Tenant Seeder Completed");
        log.info("=========================================");
    }
}
