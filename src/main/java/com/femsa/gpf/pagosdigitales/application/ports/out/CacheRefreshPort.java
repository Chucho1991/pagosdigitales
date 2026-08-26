package com.femsa.gpf.pagosdigitales.application.ports.out;

import java.util.List;

import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;

/**
 * Puerto de salida para recargar caches respaldadas por base de datos.
 */
public interface CacheRefreshPort {

    /**
     * Ejecuta todos los refrescos registrados.
     *
     * @return resultados individuales de cada cache
     */
    List<CacheRefreshResult> refreshAll();
}
