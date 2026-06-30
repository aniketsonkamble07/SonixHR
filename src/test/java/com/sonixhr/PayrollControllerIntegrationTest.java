package com.sonixhr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.dto.LoginRequest;
import com.sonixhr.dto.LoginResponse;
import com.sonixhr.dto.payroll.PayrollCalculationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantRegistrationResponse;
import com.sonixhr.enums.IndianState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class PayrollControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenHeader;

    @BeforeEach
    public void setUp() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String companyName = "Payroll Test Company " + uniqueSuffix;
        String adminEmail = "admin_" + uniqueSuffix + "@payrolltest.com";

        // Register Tenant
        TenantRegistrationRequest regRequest = TenantRegistrationRequest.builder()
                .companyName(companyName)
                .adminEmail(adminEmail)
                .adminName("Admin User")
                .adminPhone("+12345678901")
                .officeAddress("123 Test Street")
                .city("Bangalore")
                .state(IndianState.KARNATAKA)
                .country("India")
                .planCode("trial")
                .billingCycle("MONTHLY")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegistrationResponse regResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), TenantRegistrationResponse.class);

        // Login
        LoginRequest loginRequest = new LoginRequest(adminEmail, "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/tenant/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        this.tokenHeader = "Bearer " + loginResponse.getAccessToken();
    }

    @Test
    public void testCalculateEndpointRequiresAuthentication() throws Exception {
        PayrollCalculationRequest req = new PayrollCalculationRequest();
        req.setCtc(BigDecimal.valueOf(50000.00));
        req.setState(IndianState.MAHARASHTRA);
        req.setMonth(5);
        req.setYear(2025);
        req.setLopDays(BigDecimal.ZERO);
        req.setCompliantMode(true);
        req.setPfCapping(true);
        req.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        // 1. Check without auth token -> Expect 401 Unauthorized
        mockMvc.perform(post("/api/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // 2. Check with auth token -> Expect 200 OK
        mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    public void testCalculateLopDaysDynamicLengthOfMonth() throws Exception {
        // Test May (31 days)
        PayrollCalculationRequest reqMay = new PayrollCalculationRequest();
        reqMay.setCtc(BigDecimal.valueOf(50000.00));
        reqMay.setState(IndianState.MAHARASHTRA);
        reqMay.setMonth(5); // May
        reqMay.setYear(2025);
        reqMay.setLopDays(BigDecimal.ONE);
        reqMay.setCompliantMode(true);
        reqMay.setPfCapping(true);
        reqMay.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        MvcResult resultMay = mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqMay)))
                .andExpect(status().isOk())
                .andReturn();

        com.sonixhr.dto.payroll.PayrollCalculationResponse respMay = objectMapper.readValue(
                resultMay.getResponse().getContentAsString(), com.sonixhr.dto.payroll.PayrollCalculationResponse.class);

        // Basic Base = 50000 * 0.50 = 25000
        // For 31 days month (May): basicLop = 25000 / 31 * 1 = 806.45
        // Expected Basic = 25000 - 806.45 = 24193.55
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("24193.55"),
                respMay.getComponents().get("BASIC")
        );

        // Test Feb 2024 (Leap year - 29 days)
        PayrollCalculationRequest reqFebLeap = new PayrollCalculationRequest();
        reqFebLeap.setCtc(BigDecimal.valueOf(50000.00));
        reqFebLeap.setState(IndianState.MAHARASHTRA);
        reqFebLeap.setMonth(2); // Feb
        reqFebLeap.setYear(2024);
        reqFebLeap.setLopDays(BigDecimal.ONE);
        reqFebLeap.setCompliantMode(true);
        reqFebLeap.setPfCapping(true);
        reqFebLeap.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        MvcResult resultFebLeap = mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqFebLeap)))
                .andExpect(status().isOk())
                .andReturn();

        com.sonixhr.dto.payroll.PayrollCalculationResponse respFebLeap = objectMapper.readValue(
                resultFebLeap.getResponse().getContentAsString(), com.sonixhr.dto.payroll.PayrollCalculationResponse.class);

        // Basic Base = 25000
        // For 29 days month (Feb 2024): basicLop = 25000 / 29 * 1 = 862.07
        // Expected Basic = 25000 - 862.07 = 24137.93
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("24137.93"),
                respFebLeap.getComponents().get("BASIC")
        );
    }

    @Test
    public void testCalculateEndpointValidationErrors() throws Exception {
        // 1. Invalid Month (13) -> Expect 400 Bad Request
        PayrollCalculationRequest reqMonth = new PayrollCalculationRequest();
        reqMonth.setCtc(BigDecimal.valueOf(50000.00));
        reqMonth.setState(IndianState.MAHARASHTRA);
        reqMonth.setMonth(13);
        reqMonth.setYear(2025);
        reqMonth.setLopDays(BigDecimal.ZERO);
        reqMonth.setCompliantMode(true);
        reqMonth.setPfCapping(true);
        reqMonth.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqMonth)))
                .andExpect(status().isBadRequest());

        // 2. Negative CTC -> Expect 400 Bad Request
        PayrollCalculationRequest reqCtc = new PayrollCalculationRequest();
        reqCtc.setCtc(BigDecimal.valueOf(-50000.00));
        reqCtc.setState(IndianState.MAHARASHTRA);
        reqCtc.setMonth(5);
        reqCtc.setYear(2025);
        reqCtc.setLopDays(BigDecimal.ZERO);
        reqCtc.setCompliantMode(true);
        reqCtc.setPfCapping(true);
        reqCtc.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCtc)))
                .andExpect(status().isBadRequest());

        // 3. Negative LOP Days -> Expect 400 Bad Request
        PayrollCalculationRequest reqLopNeg = new PayrollCalculationRequest();
        reqLopNeg.setCtc(BigDecimal.valueOf(50000.00));
        reqLopNeg.setState(IndianState.MAHARASHTRA);
        reqLopNeg.setMonth(5);
        reqLopNeg.setYear(2025);
        reqLopNeg.setLopDays(BigDecimal.valueOf(-1));
        reqLopNeg.setCompliantMode(true);
        reqLopNeg.setPfCapping(true);
        reqLopNeg.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLopNeg)))
                .andExpect(status().isBadRequest());

        // 4. LOP Days Exceeding Month Length (29 days for Feb 2025) -> Expect 400 Bad Request
        PayrollCalculationRequest reqLopExceed = new PayrollCalculationRequest();
        reqLopExceed.setCtc(BigDecimal.valueOf(50000.00));
        reqLopExceed.setState(IndianState.MAHARASHTRA);
        reqLopExceed.setMonth(2); // Feb 2025 has 28 days
        reqLopExceed.setYear(2025);
        reqLopExceed.setLopDays(BigDecimal.valueOf(29));
        reqLopExceed.setCompliantMode(true);
        reqLopExceed.setPfCapping(true);
        reqLopExceed.setEsiPeriodStartGross(BigDecimal.valueOf(20000));

        mockMvc.perform(post("/api/payroll/calculate")
                        .header("Authorization", tokenHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLopExceed)))
                .andExpect(status().isBadRequest());
    }
}
