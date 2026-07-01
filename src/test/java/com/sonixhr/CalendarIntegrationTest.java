package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.calendar.CalendarDayDTO;
import com.sonixhr.dto.calendar.CalendarMonthDTO;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.entity.leave.PublicHoliday;
import com.sonixhr.entity.leave.TenantLeaveSettings;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.calendar.CalendarDayType;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class CalendarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PublicHolidayRepository holidayRepository;

    @Autowired
    private TenantLeaveSettingsRepository leaveSettingsRepository;

    @Test
    public void testConsolidatedCalendarForIndianAndNonIndianTenant() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // ----------------------------------------------------
        // Part 1: Test Non-Indian Tenant (US / California)
        // ----------------------------------------------------
        String usCompanyName = "US Tech " + uniqueSuffix;
        String usAdminEmail = "us_admin_" + uniqueSuffix + "@ustech.com";

        TenantRegistrationRequest usRegRequest = TenantRegistrationRequest.builder()
                .companyName(usCompanyName)
                .adminEmail(usAdminEmail)
                .adminName("US Admin")
                .adminPhone("+1555019901")
                .officeAddress("1600 Amphitheatre Pkwy")
                .city("Mountain View")
                .state(null)
                .stateText("California")
                .country("US")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult usRegResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usRegRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse usRegResponse = objectMapper.readValue(
                usRegResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);
        Long usTenantId = usRegResponse.getTenantId();

        // Check that TenantLeaveSettings contains California in stateText
        TenantLeaveSettings usLeaveSettings = leaveSettingsRepository.findById(usTenantId)
                .orElseThrow(() -> new AssertionError("Leave settings not created for US tenant"));
        assertEquals("California", usLeaveSettings.getStateText());
        assertNull(usLeaveSettings.getState());

        // Create a State Public Holiday for California in July 2026
        PublicHoliday californiaHoliday = PublicHoliday.builder()
                .tenantId(usTenantId)
                .holidayDate(LocalDate.of(2026, 7, 4))
                .name("Independence Day")
                .type("STATE")
                .region("California")
                .isRecurring(true)
                .year(2026)
                .description("National Day of the US")
                .build();
        holidayRepository.save(californiaHoliday);

        // Login to get authentication token
        LoginRequest usLoginRequest = new LoginRequest(usAdminEmail, "Admin@123");
        MvcResult usLoginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usLoginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse usLoginResponse = objectMapper.readValue(
                usLoginResult.getResponse().getContentAsString(), LoginResponse.class);
        String usTokenHeader = "Bearer " + usLoginResponse.getAccessToken();

        // Fetch Consolidated Calendar for July 2026
        MvcResult usCalResult = mockMvc.perform(get("/api/calendar/my")
                        .header("Authorization", usTokenHeader)
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andReturn();

        CalendarMonthDTO usCalendar = objectMapper.readValue(
                usCalResult.getResponse().getContentAsString(), CalendarMonthDTO.class);

        // Verify Calendar contains the Holiday (July 4th)
        assertNotNull(usCalendar);
        assertEquals(31, usCalendar.getDays().size());

        CalendarDayDTO july4th = usCalendar.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 7, 4)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("July 4th not found in calendar"));

        assertEquals(CalendarDayType.HOLIDAY, july4th.getType());
        assertEquals("Independence Day", july4th.getDisplayName());

        // ----------------------------------------------------
        // Part 2: Test Indian Tenant (India / Maharashtra)
        // ----------------------------------------------------
        String inCompanyName = "India Tech " + uniqueSuffix;
        String inAdminEmail = "in_admin_" + uniqueSuffix + "@indiastech.com";

        TenantRegistrationRequest inRegRequest = TenantRegistrationRequest.builder()
                .companyName(inCompanyName)
                .adminEmail(inAdminEmail)
                .adminName("India Admin")
                .adminPhone("+919876543210")
                .officeAddress("456 IT Lane")
                .city("Pune")
                .state(IndianState.MAHARASHTRA)
                .stateText(null)
                .country("IN")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult inRegResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inRegRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse inRegResponse = objectMapper.readValue(
                inRegResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);
        Long inTenantId = inRegResponse.getTenantId();

        // Check that TenantLeaveSettings contains MAHARASHTRA
        TenantLeaveSettings inLeaveSettings = leaveSettingsRepository.findById(inTenantId)
                .orElseThrow(() -> new AssertionError("Leave settings not created for Indian tenant"));
        assertEquals(IndianState.MAHARASHTRA, inLeaveSettings.getState());
        assertNull(inLeaveSettings.getStateText());

        // Create a State Public Holiday for Maharashtra in July 2026
        PublicHoliday maharashtraHoliday = PublicHoliday.builder()
                .tenantId(inTenantId)
                .holidayDate(LocalDate.of(2026, 7, 10))
                .name("Maharashtra Day Celebration")
                .type("STATE")
                .region("MAHARASHTRA")
                .isRecurring(true)
                .year(2026)
                .description("Maharashtra State Holiday")
                .build();
        holidayRepository.save(maharashtraHoliday);

        // Login
        LoginRequest inLoginRequest = new LoginRequest(inAdminEmail, "Admin@123");
        MvcResult inLoginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inLoginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse inLoginResponse = objectMapper.readValue(
                inLoginResult.getResponse().getContentAsString(), LoginResponse.class);
        String inTokenHeader = "Bearer " + inLoginResponse.getAccessToken();

        // Fetch Consolidated Calendar for July 2026
        MvcResult inCalResult = mockMvc.perform(get("/api/calendar/my")
                        .header("Authorization", inTokenHeader)
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andReturn();

        CalendarMonthDTO inCalendar = objectMapper.readValue(
                inCalResult.getResponse().getContentAsString(), CalendarMonthDTO.class);

        // Verify Calendar contains the Holiday (July 10th)
        assertNotNull(inCalendar);
        CalendarDayDTO july10th = inCalendar.getDays().stream()
                .filter(day -> day.getDate().equals(LocalDate.of(2026, 7, 10)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("July 10th not found in calendar"));

        assertEquals(CalendarDayType.HOLIDAY, july10th.getType());
        assertEquals("Maharashtra Day Celebration", july10th.getDisplayName());
    }
}
