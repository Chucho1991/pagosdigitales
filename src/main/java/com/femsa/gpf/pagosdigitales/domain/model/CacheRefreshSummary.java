package com.femsa.gpf.pagosdigitales.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Resumen de una solicitud de refresco de todas las caches.
 *
 * @param refreshedAt instante en que finalizo la operacion
 * @param results resultados individuales de las caches
 */
public record CacheRefreshSummary(Instant refreshedAt, List<CacheRefreshResult> results) {

    /**
     * Crea un resumen con una copia inmutable de los resultados.
     */
    public CacheRefreshSummary {
        results = List.copyOf(results);
    }

    /**
     * Obtiene el numero de caches actualizadas.
     *
     * @return total de refrescos exitosos
     */
    public long successfulCount() {
        return results.stream().filter(CacheRefreshResult::successful).count();
    }

    /**
     * Obtiene el numero de caches que conservaron su valor anterior.
     *
     * @return total de refrescos fallidos
     */
    public long failedCount() {
        return results.size() - successfulCount();
    }
}
