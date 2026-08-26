package com.femsa.gpf.pagosdigitales;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.femsa.gpf.pagosdigitales.api.controller.CacheRefreshController;
import com.femsa.gpf.pagosdigitales.application.ports.in.RefreshCachesUseCase;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;

@WebMvcTest(CacheRefreshController.class)
class CacheRefreshAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshCachesUseCase refreshCachesUseCase;

    @MockitoBean
    private IntegrationLogService integrationLogService;

    @BeforeEach
    void setup() {
        when(refreshCachesUseCase.refreshAll()).thenReturn(new CacheRefreshSummary(
                Instant.parse("2026-08-26T10:00:00Z"),
                List.of(new CacheRefreshResult("test", List.of("TEST_TABLE"), true))));
    }

    @Test
    void refreshAllAllowsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/v1/cache/refresh"))
                .andExpect(status().isOk());
    }
}
