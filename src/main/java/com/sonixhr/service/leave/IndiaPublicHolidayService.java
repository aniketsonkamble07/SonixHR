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

    // Simplified calculation methods - in production, use proper calendar APIs
    private LocalDate calculateDiwaliDate(int year) {
        // Rough approximation - Diwali is in October/November
        return LocalDate.of(year, Month.OCTOBER, 24);
    }

    private LocalDate calculateHoliDate(int year) {
        return LocalDate.of(year, Month.MARCH, 8);
    }

    private LocalDate calculateDussehraDate(int year) {
        return LocalDate.of(year, Month.OCTOBER, 5);
    }

    private LocalDate calculateGaneshChaturthiDate(int year) {
        return LocalDate.of(year, Month.AUGUST, 31);
    }

    private LocalDate calculateEidDate(int year) {
        return LocalDate.of(year, Month.JUNE, 15);
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