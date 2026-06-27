package com.sonixhr.bootstrap;

import com.sonixhr.enums.IndianState;
import com.sonixhr.entity.payroll.StateProfessionalTaxConfig;
import com.sonixhr.entity.payroll.StatutoryRateConfig;
import com.sonixhr.repository.payroll.StateProfessionalTaxConfigRepository;
import com.sonixhr.repository.payroll.StatutoryRateConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
@SuppressWarnings("null")
public class PayrollDataInitializer implements ApplicationRunner {

    private final StatutoryRateConfigRepository statutoryRateConfigRepository;
    private final StateProfessionalTaxConfigRepository stateProfessionalTaxConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("Payroll Data Initializer Started");
        log.info("=========================================");

        seedStatutoryRates();
        seedProfessionalTaxSlabs();

        log.info("=========================================");
        log.info("Payroll Data Initializer Completed");
        log.info("=========================================");
    }

    private void seedStatutoryRates() {
        LocalDate epoch = LocalDate.of(2020, 1, 1);
        if (!statutoryRateConfigRepository.existsByEffectiveFrom(epoch)) {
            log.info("Seeding statutory rates for India (Labour Code 2025/2026)...");

            // EPF Employee Contribution (12% of Wages Base, capped at 1800)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("EPF_EE")
                    .rate(BigDecimal.valueOf(0.1200))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(1800.00))
                    .effectiveFrom(epoch)
                    .build());

            // EPF Employer Contribution (Split component: gets the remainder of 12%)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("EPF_ER")
                    .rate(BigDecimal.valueOf(0.1200))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(null)
                    .capAmount(null)
                    .effectiveFrom(epoch)
                    .build());

            // EPS Pension Share (8.33% of Wages Base, capped at 1250)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("EPS_ER")
                    .rate(BigDecimal.valueOf(0.0833))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(1250.00))
                    .effectiveFrom(epoch)
                    .build());

            // EDLI Insurance Premium (0.5% of Wages Base, capped at 75)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("EDLI")
                    .rate(BigDecimal.valueOf(0.0050))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(15000.00))
                    .capAmount(BigDecimal.valueOf(75.00))
                    .effectiveFrom(epoch)
                    .build());

            // ESI Employee Contribution (0.75% of Wages Base)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("ESI_EE")
                    .rate(BigDecimal.valueOf(0.0075))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(21000.00))
                    .capAmount(null)
                    .effectiveFrom(epoch)
                    .build());

            // ESI Employer Contribution (3.25% of Wages Base)
            statutoryRateConfigRepository.save(StatutoryRateConfig.builder()
                    .componentCode("ESI_ER")
                    .rate(BigDecimal.valueOf(0.0325))
                    .wageBase("WAGES_BASE")
                    .ceilingAmount(BigDecimal.valueOf(21000.00))
                    .capAmount(null)
                    .effectiveFrom(epoch)
                    .build());

            log.info("✅ Seeded default statutory rates.");
        }
    }

    private void seedProfessionalTaxSlabs() {
        LocalDate epoch = LocalDate.of(2020, 1, 1);
        if (!stateProfessionalTaxConfigRepository.existsByEffectiveFrom(epoch)) {
            log.info("Seeding state professional tax configurations...");

            // Karnataka (KA)
            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KA)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(24999.99))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.KA)
                    .salaryRangeMin(BigDecimal.valueOf(24999.99))
                    .salaryRangeMax(null)
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());

            // Maharashtra (MH)
            // Maharashtra Slabs:
            // 0 - 7500 -> 0
            // > 7500 to 10000 -> 175
            // > 10000 -> 200 (except February, where it is 300)
            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MH)
                    .salaryRangeMin(BigDecimal.ZERO)
                    .salaryRangeMax(BigDecimal.valueOf(7500.00))
                    .amount(BigDecimal.ZERO)
                    .effectiveFrom(epoch)
                    .build());

            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MH)
                    .salaryRangeMin(BigDecimal.valueOf(7500.00))
                    .salaryRangeMax(BigDecimal.valueOf(10000.00))
                    .amount(BigDecimal.valueOf(175.00))
                    .effectiveFrom(epoch)
                    .build());

            // February specific: ₹300
            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MH)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(null)
                    .applicableMonth(2) // February
                    .amount(BigDecimal.valueOf(300.00))
                    .effectiveFrom(epoch)
                    .build());

            // Default other months: ₹200
            stateProfessionalTaxConfigRepository.save(StateProfessionalTaxConfig.builder()
                    .stateCode(IndianState.MH)
                    .salaryRangeMin(BigDecimal.valueOf(10000.00))
                    .salaryRangeMax(null)
                    .applicableMonth(null) // Applies to non-February months
                    .amount(BigDecimal.valueOf(200.00))
                    .effectiveFrom(epoch)
                    .build());

            log.info("✅ Seeded state Professional Tax configurations.");
        }
    }
}
