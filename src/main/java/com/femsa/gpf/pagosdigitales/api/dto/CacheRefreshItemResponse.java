package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

/**
 * Resultado HTTP del refresco de una cache.
 *
 * @param cacheName nombre funcional de la cache
 * @param tables tablas de origen involucradas
 * @param status estado UPDATED o FAILED
 */
public record CacheRefreshItemResponse(String cacheName, List<String> tables, String status) {
}
