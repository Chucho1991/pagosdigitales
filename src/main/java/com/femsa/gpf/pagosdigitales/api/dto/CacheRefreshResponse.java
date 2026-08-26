package com.femsa.gpf.pagosdigitales.api.dto;

import java.time.Instant;
import java.util.List;

import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;

/**
 * Respuesta del refresco global de caches.
 *
 * @param refreshedAt instante en que finalizo el refresco
 * @param total total de caches procesadas
 * @param successful total de caches actualizadas
 * @param failed total de caches que conservaron su valor anterior
 * @param caches detalle por cache
 */
public record CacheRefreshResponse(
        Instant refreshedAt,
        int total,
        long successful,
        long failed,
        List<CacheRefreshItemResponse> caches) {

    /**
     * Convierte el resultado del dominio al contrato HTTP.
     *
     * @param summary resumen del caso de uso
     * @return respuesta serializable del endpoint
     */
    public static CacheRefreshResponse from(CacheRefreshSummary summary) {
        List<CacheRefreshItemResponse> items = summary.results().stream()
                .map(result -> new CacheRefreshItemResponse(
                        result.cacheName(),
                        result.tables(),
                        result.successful() ? "UPDATED" : "FAILED"))
                .toList();
        return new CacheRefreshResponse(
                summary.refreshedAt(),
                items.size(),
                summary.successfulCount(),
                summary.failedCount(),
                items);
    }
}
