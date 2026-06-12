package com.sonixhr.service.leave;


import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndiaPublicHolidayService {

    private final PublicHolidayRepository holidayRepository;
    private final TenantLeaveSettingsRepository settingsRepository;

    /**
     * Generate India public holidays for a specific year
     */
    @Transactional
    public List<PublicHoliday> generateIndiaHolidaysForYear(Long tenantId, int year) {
        log.info("Generating India public holidays for tenant {} for year {}", tenantId, year);

        List<PublicHoliday> holidays = new ArrayList<>();

        // National Holidays (fixed dates)
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.JANUARY, 26), "Republic Day", "NATIONAL", "NATIONAL", year));
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.AUGUST, 15), "Independence Day", "NATIONAL", "NATIONAL", year));
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.OCTOBER, 2), "Gandhi Jayanti", "NATIONAL", "NATIONAL", year));

        // Fixed date holidays
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.MAY, 1), "Maharashtra Day", "STATE", "MAHARASHTRA", year));
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.JANUARY, 1), "New Year's Day", "FESTIVAL", "NATIONAL", year));
        holidays.add(createHoliday(tenantId, LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day", "RELIGIOUS", "NATIONAL", year));

        // Variable date holidays (need calculation)
        holidays.add(createDiwaliHoliday(tenantId, year));
        holidays.add(createHoliHoliday(tenantId, year));
        holidays.add(createDussehraHoliday(tenantId, year));
        holidays.add(createGaneshChaturthiHoliday(tenantId, year, "MAHARASHTRA"));
        holidays.add(createEidHoliday(tenantId, year));

        // Save all holidays
        int savedCount = 0;
        for (PublicHoliday holiday : holidays) {
            try {
                if (!holidayRepository.existsByTenantIdAndHolidayDate(tenantId, holiday.getHolidayDate())) {
                    holidayRepository.save(holiday);
                    savedCount++;
                    log.info("Saved holiday: {} on {}", holiday.getName(), holiday.getHolidayDate());
                } else {
                    log.debug("Holiday already exists: {} on {}", holiday.getName(), holiday.getHolidayDate());
                }
            } catch (Exception e) {
                log.error("Failed to save holiday: {} on {}", holiday.getName(), holiday.getHolidayDate(), e);
            }
        }

        log.info("Generated {} new holidays for tenant {} in year {}", savedCount, tenantId, year);
        return holidays;
    }

    private PublicHoliday createHoliday(Long tenantId, LocalDate date, String name, String type, String region, int year) {
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(date)
                .name(name)
                .type(type)
                .region(region)
                .year(year)
                .isRecurring(false)
                .description(type.equals("NATIONAL") ? "National Holiday" : "Regional Holiday")
                .build();
    }

    private PublicHoliday createDiwaliHoliday(Long tenantId, int year) {
        // Diwali typically in October/November - simplified calculation
        LocalDate diwaliDate = calculateDiwaliDate(year);
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(diwaliDate)
                .name("Diwali")
                .type("FESTIVAL")
                .region("NATIONAL")
                .year(year)
                .isRecurring(false)
                .description("Festival of Lights")
                .build();
    }

    private PublicHoliday createHoliHoliday(Long tenantId, int year) {
        LocalDate holiDate = calculateHoliDate(year);
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(holiDate)
                .name("Holi")
                .type("FESTIVAL")
                .region("NATIONAL")
                .year(year)
                .isRecurring(false)
                .description("Festival of Colors")
                .build();
    }

    private PublicHoliday createDussehraHoliday(Long tenantId, int year) {
        LocalDate dussehraDate = calculateDussehraDate(year);
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(dussehraDate)
                .name("Dussehra")
                .type("FESTIVAL")
                .region("NATIONAL")
                .year(year)
                .isRecurring(false)
                .description("Vijaya Dashami")
                .build();
    }

    private PublicHoliday createGaneshChaturthiHoliday(Long tenantId, int year, String state) {
        LocalDate ganeshDate = calculateGaneshChaturthiDate(year);
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(ganeshDate)
                .name("Ganesh Chaturthi")
                .type("FESTIVAL")
                .region(state)
                .year(year)
                .isRecurring(false)
                .description("Ganesh Festival")
                .build();
    }

    private PublicHoliday createEidHoliday(Long tenantId, int year) {
        LocalDate eidDate = calculateEidDate(year);
        return PublicHoliday.builder()
                .tenantId(tenantId)
                .holidayDate(eidDate)
                .name("Eid-ul-Fitr")
                .type("RELIGIOUS")
                .region("NATIONAL")
                .year(year)
                .isRecurring(false)
                .description("Eid Celebration")
                .build();
    }

    // Dynamic calculation helper methods for Indian variable holidays (2023-2030)
    private LocalDate calculateDiwaliDate(int year) {
        switch (year) {
            case 2023: return LocalDate.of(2023, Month.NOVEMBER, 12);
            case 2024: return LocalDate.of(2024, Month.OCTOBER, 31);
            case 2025: return LocalDate.of(2025, Month.OCTOBER, 20);
            case 2026: return LocalDate.of(2026, Month.NOVEMBER, 8);
            case 2027: return LocalDate.of(2027, Month.OCTOBER, 29);
            case 2028: return LocalDate.of(2028, Month.NOVEMBER, 17);
            case 2029: return LocalDate.of(2029, Month.NOVEMBER, 5);
            case 2030: return LocalDate.of(2030, Month.OCTOBER, 26);
            default: return LocalDate.of(year, Month.OCTOBER, 24); // Default fallback
        }
    }

    private LocalDate calculateHoliDate(int year) {
        switch (year) {
            case 2023: return LocalDate.of(2023, Month.MARCH, 8);
            case 2024: return LocalDate.of(2024, Month.MARCH, 25);
            case 2025: return LocalDate.of(2025, Month.MARCH, 14);
            case 2026: return LocalDate.of(2026, Month.MARCH, 4);
            case 2027: return LocalDate.of(2027, Month.MARCH, 22);
            case 2028: return LocalDate.of(2028, Month.MARCH, 11);
            case 2029: return LocalDate.of(2029, Month.FEBRUARY, 28);
            case 2030: return LocalDate.of(2030, Month.MARCH, 19);
            default: return LocalDate.of(year, Month.MARCH, 8); // Default fallback
        }
    }

    private LocalDate calculateDussehraDate(int year) {
        switch (year) {
            case 2023: return LocalDate.of(2023, Month.OCTOBER, 24);
            case 2024: return LocalDate.of(2024, Month.OCTOBER, 12);
            case 2025: return LocalDate.of(2025, Month.OCTOBER, 2);
            case 2026: return LocalDate.of(2026, Month.OCTOBER, 20);
            case 2027: return LocalDate.of(2027, Month.OCTOBER, 9);
            case 2028: return LocalDate.of(2028, Month.OCTOBER, 27);
            case 2029: return LocalDate.of(2029, Month.OCTOBER, 17);
            case 2030: return LocalDate.of(2030, Month.OCTOBER, 6);
            default: return LocalDate.of(year, Month.OCTOBER, 5); // Default fallback
        }
    }

    private LocalDate calculateGaneshChaturthiDate(int year) {
        switch (year) {
            case 2023: return LocalDate.of(2023, Month.SEPTEMBER, 19);
            case 2024: return LocalDate.of(2024, Month.SEPTEMBER, 7);
            case 2025: return LocalDate.of(2025, Month.AUGUST, 27);
            case 2026: return LocalDate.of(2026, Month.SEPTEMBER, 14);
            case 2027: return LocalDate.of(2027, Month.SEPTEMBER, 4);
            case 2028: return LocalDate.of(2028, Month.SEPTEMBER, 22);
            case 2029: return LocalDate.of(2029, Month.SEPTEMBER, 11);
            case 2030: return LocalDate.of(2030, Month.SEPTEMBER, 1);
            default: return LocalDate.of(year, Month.AUGUST, 31); // Default fallback
        }
    }

    private LocalDate calculateEidDate(int year) {
        switch (year) {
            case 2023: return LocalDate.of(2023, Month.APRIL, 22);
            case 2024: return LocalDate.of(2024, Month.APRIL, 10);
            case 2025: return LocalDate.of(2025, Month.MARCH, 31);
            case 2026: return LocalDate.of(2026, Month.MARCH, 20);
            case 2027: return LocalDate.of(2027, Month.MARCH, 9);
            case 2028: return LocalDate.of(2028, Month.FEBRUARY, 26);
            case 2029: return LocalDate.of(2029, Month.FEBRUARY, 15);
            case 2030: return LocalDate.of(2030, Month.FEBRUARY, 4);
            default: return LocalDate.of(year, Month.JUNE, 15); // Default fallback
        }
    }

    /**
     * Initialize holidays for a tenant (called when tenant is created)
     */
    @Transactional
    public void initializeHolidaysForNewTenant(Long tenantId) {
        log.info("Initializing holidays for new tenant: {}", tenantId);

        // Create default leave settings
        if (!settingsRepository.existsByTenantId(tenantId)) {
            TenantLeaveSettings settings = TenantLeaveSettings.builder()
                    .tenantId(tenantId)
                    .build();
            settingsRepository.save(settings);
            log.info("Created default leave settings for tenant: {}", tenantId);
        }

        // Generate holidays for current year and next year
        int currentYear = LocalDate.now().getYear();
        generateIndiaHolidaysForYear(tenantId, currentYear);
        generateIndiaHolidaysForYear(tenantId, currentYear + 1);

        log.info("Holidays initialized for tenant: {}", tenantId);
    }
}