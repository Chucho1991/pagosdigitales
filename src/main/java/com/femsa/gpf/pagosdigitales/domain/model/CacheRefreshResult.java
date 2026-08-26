package com.femsa.gpf.pagosdigitales.domain.model;

import java.util.List;

/**
 * Resultado del refresco de una cache respaldada por tablas de base de datos.
 *
 * @param cacheName nombre funcional de la cache
 * @param tables tablas consultadas durante el refresco
 * @param successful indica si la cache fue reemplazada correctamente
 */
public record CacheRefreshResult(String cacheName, List<String> tables, boolean successful) {

    /**
     * Crea un resultado con una copia inmutable de las tablas.
     */
    public CacheRefreshResult {
        tables = List.copyOf(tables);
    }
}
