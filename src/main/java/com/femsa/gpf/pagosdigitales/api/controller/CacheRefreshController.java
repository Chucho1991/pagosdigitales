package com.femsa.gpf.pagosdigitales.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.CacheRefreshResponse;
import com.femsa.gpf.pagosdigitales.application.ports.in.RefreshCachesUseCase;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Adaptador REST para operaciones administrativas sobre caches.
 */
@RestController
@RequestMapping("/api/v1/cache")
public class CacheRefreshController {

    private final RefreshCachesUseCase refreshCachesUseCase;

    /**
     * Crea el controlador de refresco de caches.
     *
     * @param refreshCachesUseCase caso de uso de refresco global
     */
    public CacheRefreshController(RefreshCachesUseCase refreshCachesUseCase) {
        this.refreshCachesUseCase = refreshCachesUseCase;
    }

    /**
     * Refresca todas las caches de tablas configuradas.
     *
     * @return detalle de caches actualizadas y fallidas
     */
    @Operation(summary = "Refresca todas las caches respaldadas por base de datos")
    @ApiResponse(responseCode = "200", description = "Todas las caches fueron actualizadas")
    @ApiResponse(responseCode = "207", description = "Algunas caches no pudieron actualizarse")
    @ApiResponse(responseCode = "503", description = "Ninguna cache pudo actualizarse")
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CacheRefreshResponse> refreshAll() {
        CacheRefreshSummary summary = refreshCachesUseCase.refreshAll();
        HttpStatus status = resolveStatus(summary);
        return ResponseEntity.status(status).body(CacheRefreshResponse.from(summary));
    }

    private HttpStatus resolveStatus(CacheRefreshSummary summary) {
        if (summary.failedCount() == 0) {
            return HttpStatus.OK;
        }
        if (summary.successfulCount() == 0) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.MULTI_STATUS;
    }
}
