package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver headers de pasarela por codigo de billetera.
 */
@Log4j2
@Service
public class ProviderHeaderService {

    private static final String SELECT_HEADERS = "SELECT CODIGO_BILLETERA, HEADER_NOMBRE, HEADER_VALOR "
            + "FROM TUKUNAFUNC.IN_PASARELA_HEADERS";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<Integer, Map<String, String>> headersByProviderCode = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public ProviderHeaderService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Inicializa cache al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de headers cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<Integer, Map<String, String>> loaded = loadHeadersFromDb();
            this.headersByProviderCode = Map.copyOf(loaded);
            log.info("Cache de IN_PASARELA_HEADERS actualizada. Proveedores con headers: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache IN_PASARELA_HEADERS. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene headers por codigo de proveedor.
     *
     * @param providerCode codigo de billetera
     * @return mapa inmutable de headers configurados
     */
    public Map<String, String> getHeadersByProviderCode(Integer providerCode) {
        if (providerCode == null) {
            return Map.of();
        }
        return headersByProviderCode.getOrDefault(providerCode, Map.of());
    }

    private Map<Integer, Map<String, String>> loadHeadersFromDb() throws Exception {
        Map<Integer, Map<String, String>> temp = new HashMap<>();
        databaseExecutor.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_HEADERS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int providerCodeValue = rs.getInt("CODIGO_BILLETERA");
                    if (rs.wasNull()) {
                        continue;
                    }
                    String headerName = rs.getString("HEADER_NOMBRE");
                    String headerValue = rs.getString("HEADER_VALOR");
                    if (headerName == null || headerName.isBlank() || headerValue == null || headerValue.isBlank()) {
                        continue;
                    }
                    temp.computeIfAbsent(providerCodeValue, value -> new LinkedHashMap<>())
                            .put(headerName.trim(), headerValue.trim());
                }
            }
        });
        Map<Integer, Map<String, String>> immutable = new HashMap<>();
        temp.forEach((provider, headers) -> immutable.put(provider, Map.copyOf(headers)));
        return immutable;
    }
}
