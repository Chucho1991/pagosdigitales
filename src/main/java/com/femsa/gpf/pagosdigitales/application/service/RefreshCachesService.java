package com.femsa.gpf.pagosdigitales.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.application.ports.in.RefreshCachesUseCase;
import com.femsa.gpf.pagosdigitales.application.ports.out.CacheRefreshPort;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshSummary;

/**
 * Servicio de aplicacion que coordina el refresco global de caches.
 */
@Service
public class RefreshCachesService implements RefreshCachesUseCase {

    private final CacheRefreshPort cacheRefreshPort;

    /**
     * Crea el servicio con el puerto de refresco configurado.
     *
     * @param cacheRefreshPort puerto que ejecuta los refrescos
     */
    public RefreshCachesService(CacheRefreshPort cacheRefreshPort) {
        this.cacheRefreshPort = cacheRefreshPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheRefreshSummary refreshAll() {
        List<CacheRefreshResult> results = cacheRefreshPort.refreshAll();
        return new CacheRefreshSummary(Instant.now(), results);
    }
}
