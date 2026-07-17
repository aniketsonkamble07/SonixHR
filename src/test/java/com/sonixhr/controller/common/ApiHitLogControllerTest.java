package com.sonixhr.controller.common;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.service.common.ApiHitLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ApiHitLogControllerTest {

    @Mock private ApiHitLogService apiHitLogService;
    private ApiHitLogController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ApiHitLogController(apiHitLogService);
    }

    @Test
    public void testGetTenantApiLogs_Enabled() {
        Employee currentUser = new Employee();
        currentUser.setTenantId(10L);
        Pageable pageable = Pageable.unpaged();

        when(apiHitLogService.isApiLoggingEnabled(10L)).thenReturn(true);
        ApiHitLog logEntry = ApiHitLog.builder().requestUri("/api/test").httpMethod("GET").build();
        Page<ApiHitLog> page = new PageImpl<>(List.of(logEntry));
        when(apiHitLogService.getTenantLogs(eq(10L), eq(pageable))).thenReturn(page);

        ResponseEntity<Page<ApiHitLog>> response = controller.getTenantApiLogs(currentUser, pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("/api/test", response.getBody().getContent().get(0).getRequestUri());
    }

    @Test
    public void testGetTenantApiLogs_Disabled() {
        Employee currentUser = new Employee();
        currentUser.setTenantId(10L);
        Pageable pageable = Pageable.unpaged();

        when(apiHitLogService.isApiLoggingEnabled(10L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            controller.getTenantApiLogs(currentUser, pageable);
        });

        assertEquals("API hit logging features are currently disabled for your organization.", exception.getMessage());
        verify(apiHitLogService, never()).getTenantLogs(anyLong(), any());
    }

    @Test
    public void testToggleApiLogging() {
        Employee currentUser = new Employee();
        currentUser.setTenantId(10L);

        ResponseEntity<Map<String, Object>> response = controller.toggleApiLogging(currentUser, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(10L, response.getBody().get("tenantId"));
        assertFalse((Boolean) response.getBody().get("apiLoggingEnabled"));

        verify(apiHitLogService, times(1)).toggleApiLogging(10L, false);
    }
}
