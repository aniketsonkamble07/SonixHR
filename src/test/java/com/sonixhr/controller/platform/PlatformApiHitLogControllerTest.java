package com.sonixhr.controller.platform;

import com.sonixhr.entity.common.ApiHitLog;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PlatformApiHitLogControllerTest {

    @Mock private ApiHitLogService apiHitLogService;
    private PlatformApiHitLogController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PlatformApiHitLogController(apiHitLogService);
    }

    @Test
    public void testGetAllApiLogs() {
        Pageable pageable = Pageable.unpaged();
        ApiHitLog logEntry = ApiHitLog.builder().requestUri("/api/platform/test").httpMethod("GET").build();
        Page<ApiHitLog> page = new PageImpl<>(List.of(logEntry));
        when(apiHitLogService.getAllLogs(eq(pageable))).thenReturn(page);

        ResponseEntity<Page<ApiHitLog>> response = controller.getAllApiLogs(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("/api/platform/test", response.getBody().getContent().get(0).getRequestUri());
    }
}
