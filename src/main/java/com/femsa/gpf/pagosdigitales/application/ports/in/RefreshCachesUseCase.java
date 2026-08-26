package com.femsa.gpf.pagosdigitales.application.ports.in;

import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;

/**
 * Caso de uso para refrescar todos los catalogos almacenados en memoria.
 */
public interface RefreshCachesUseCase {

    /**
     * Actualiza todas las caches desde sus tablas de origen.
     *
     * @return resumen de los refrescos ejecutados
     */
    CacheRefreshSummary refreshAll();
}
