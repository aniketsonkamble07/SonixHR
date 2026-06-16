package com.sonixhr.controller.calendar;

import com.sonixhr.dto.calendar.CalendarMonthDTO;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.calendar.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.userdetails.UserDetailsService;
import com.sonixhr.security.JwtAuthFilter;
import com.sonixhr.security.JwtAuthenticationEntryPoint;
import com.sonixhr.security.JwtAccessDeniedHandler;
import com.sonixhr.security.CustomPermissionEvaluator;
import com.sonixhr.security.SecurityUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarController.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarService calendarService;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean(name = "employeeDetailsService")
    private UserDetailsService employeeDetailsService;

    @MockBean(name = "platformUserDetailsService")
    private UserDetailsService platformUserDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @MockBean
    private JwtAccessDeniedHandler accessDeniedHandler;

    @MockBean
    private CustomPermissionEvaluator permissionEvaluator;

    @MockBean
    private SecurityUtils securityUtils;

    private Employee mockEmployee;
    private Employee mockManager;

    @BeforeEach
    void setUp() {
        mockEmployee = new Employee();
        mockEmployee.setId(1L);
        mockEmployee.setFirstName("John");
        mockEmployee.setLastName("Doe");
        mockEmployee.setEmail("john@example.com");

        mockManager = new Employee();
        mockManager.setId(2L);
        mockManager.setFirstName("Jane");
        mockManager.setLastName("Manager");
        mockManager.setEmail("jane@example.com");

        mockEmployee.setManager(mockManager);

        UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(mockEmployee, null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getMyCalendar_shouldReturnOk() throws Exception {
        CalendarMonthDTO mockDto = CalendarMonthDTO.builder()
                .year(2026)
                .month(6)
                .days(java.util.List.of())
                .build();

        when(calendarService.getEmployeeCalendar(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/calendar/my")
                        .param("year", "2026")
                        .param("month", "6")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getEmployeeCalendar_asManager_shouldReturnOk() throws Exception {
        // Set manager as the authenticated principal
        UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(mockManager, null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        CalendarMonthDTO mockDto = CalendarMonthDTO.builder()
                .year(2026)
                .month(6)
                .days(java.util.List.of())
                .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(mockEmployee));
        when(calendarService.getEmployeeCalendar(eq(1L), any(), anyInt(), anyInt()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/calendar/employee/1")
                        .param("year", "2026")
                        .param("month", "6")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getEmployeeCalendar_asUnauthorized_shouldReturnBadRequest() throws Exception {
        // Authenticate as a different employee (not manager, not self)
        Employee otherEmployee = new Employee();
        otherEmployee.setId(3L);
        otherEmployee.setFirstName("Other");
        otherEmployee.setLastName("Employee");
        otherEmployee.setEmail("other@example.com");

        UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(otherEmployee, null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(mockEmployee));

        mockMvc.perform(get("/api/calendar/employee/1")
                        .param("year", "2026")
                        .param("month", "6")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // Throwing BusinessException maps to 400 Bad Request
    }
}
