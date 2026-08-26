package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.femsa.gpf.pagosdigitales.api.controller.CacheRefreshController;
import com.femsa.gpf.pagosdigitales.api.dto.CacheRefreshResponse;
import com.femsa.gpf.pagosdigitales.application.ports.in.RefreshCachesUseCase;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;

class CacheRefreshControllerTest {

    private static final Instant REFRESHED_AT = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void refreshAllReturnsOkWhenEveryCacheIsUpdated() {
        RefreshCachesUseCase useCase = mock(RefreshCachesUseCase.class);
        when(useCase.refreshAll()).thenReturn(summary(true, true));

        ResponseEntity<CacheRefreshResponse> response = new CacheRefreshController(useCase).refreshAll();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().successful()).isEqualTo(2);
        assertThat(response.getBody().failed()).isZero();
    }

    @Test
    void refreshAllReturnsMultiStatusWhenOneCacheFails() {
        RefreshCachesUseCase useCase = mock(RefreshCachesUseCase.class);
        when(useCase.refreshAll()).thenReturn(summary(true, false));

        ResponseEntity<CacheRefreshResponse> response = new CacheRefreshController(useCase).refreshAll();

        assertThat(response.getStatusCode().value()).isEqualTo(207);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().successful()).isEqualTo(1);
        assertThat(response.getBody().failed()).isEqualTo(1);
        assertThat(response.getBody().caches().get(1).status()).isEqualTo("FAILED");
    }

    @Test
    void refreshAllReturnsServiceUnavailableWhenEveryCacheFails() {
        RefreshCachesUseCase useCase = mock(RefreshCachesUseCase.class);
        when(useCase.refreshAll()).thenReturn(summary(false, false));

        ResponseEntity<CacheRefreshResponse> response = new CacheRefreshController(useCase).refreshAll();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().successful()).isZero();
        assertThat(response.getBody().failed()).isEqualTo(2);
    }

    private CacheRefreshSummary summary(boolean firstSuccessful, boolean secondSuccessful) {
        return new CacheRefreshSummary(REFRESHED_AT, List.of(
                new CacheRefreshResult("first", List.of("TABLE_1"), firstSuccessful),
                new CacheRefreshResult("second", List.of("TABLE_2"), secondSuccessful)));
    }
}
