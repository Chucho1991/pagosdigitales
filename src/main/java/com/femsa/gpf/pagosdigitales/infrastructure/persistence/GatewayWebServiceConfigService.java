package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLSyntaxErrorException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver configuracion de web services por codigo de billetera y ws_key.
 */
@Log4j2
@Service
public class GatewayWebServiceConfigService {

    private static final String SELECT_WS_CONFIG_URL = "SELECT CODIGO_BILLETERA, WS_KEY, ENABLED, "
            + "TIPO_CONEXION, METODO_HTTP, TIPO_REQUEST, URL AS SERVICE_URI "
            + "FROM TUKUNAFUNC.IN_PASARELA_WS";
    private static final String SELECT_WS_CONFIG_URI = "SELECT CODIGO_BILLETERA, WS_KEY, ENABLED, "
            + "TIPO_CONEXION, METODO_HTTP, TIPO_REQUEST, URI AS SERVICE_URI "
            + "FROM TUKUNAFUNC.IN_PASARELA_WS";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<Integer, Map<String, WebServiceConfig>> configByProvider = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public GatewayWebServiceConfigService(DatabaseExecutor databaseExecutor) {
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
     * Refresca la cache de configuracion cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<Integer, Map<String, WebServiceConfig>> loaded = loadConfigFromDb();
            this.configByProvider = Map.copyOf(loaded);
            log.info("Cache de IN_PASARELA_WS actualizada. Proveedores con servicios: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache IN_PASARELA_WS. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene la configuracion activa de un servicio por proveedor y ws_key.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del web service
     * @return configuracion activa si existe
     */
    public Optional<WebServiceConfig> getActiveConfig(Integer providerCode, String wsKey) {
        if (providerCode == null || wsKey == null || wsKey.isBlank()) {
            return Optional.empty();
        }
        Map<String, WebServiceConfig> byWsKey = configByProvider.get(providerCode);
        if (byWsKey == null) {
            return Optional.empty();
        }
        WebServiceConfig config = byWsKey.get(normalizeWsKey(wsKey));
        if (config == null || !config.enabled()) {
            return Optional.empty();
        }
        if (!"REST".equalsIgnoreCase(config.connectionType())) {
            return Optional.empty();
        }
        if (config.method() == null || config.method().isBlank() || config.uri() == null || config.uri().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(config);
    }

    /**
     * Indica si el servicio esta activo para un proveedor.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del web service
     * @return true si existe configuracion activa
     */
    public boolean isActive(Integer providerCode, String wsKey) {
        return getActiveConfig(providerCode, wsKey).isPresent();
    }

    private Map<Integer, Map<String, WebServiceConfig>> loadConfigFromDb() throws Exception {
        try {
            return loadConfigFromDb(SELECT_WS_CONFIG_URL);
        } catch (SQLSyntaxErrorException e) {
            if (!isInvalidIdentifier(e)) {
                throw e;
            }
            log.warn("Columna URL no valida en IN_PASARELA_WS. Se intenta fallback con columna URI.");
            return loadConfigFromDb(SELECT_WS_CONFIG_URI);
        }
    }

    private Map<Integer, Map<String, WebServiceConfig>> loadConfigFromDb(String sql) throws Exception {
        Map<Integer, Map<String, WebServiceConfig>> temp = new HashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int providerCode = rs.getInt("CODIGO_BILLETERA");
                    if (rs.wasNull()) {
                        continue;
                    }
                    String wsKey = normalizeWsKey(rs.getString("WS_KEY"));
                    if (wsKey.isBlank()) {
                        continue;
                    }
                    WebServiceConfig config = new WebServiceConfig(
                            providerCode,
                            wsKey,
                            "S".equalsIgnoreCase(trimToEmpty(rs.getString("ENABLED"))),
                            trimToEmpty(rs.getString("TIPO_CONEXION")),
                            trimToEmpty(rs.getString("METODO_HTTP")).toUpperCase(Locale.ROOT),
                            trimToEmpty(rs.getString("TIPO_REQUEST")),
                            trimToEmpty(rs.getString("SERVICE_URI")));
                    temp.computeIfAbsent(providerCode, value -> new LinkedHashMap<>()).put(wsKey, config);
                }
            }
        });
        Map<Integer, Map<String, WebServiceConfig>> immutable = new HashMap<>();
        temp.forEach((provider, byWsKey) -> immutable.put(provider, Map.copyOf(byWsKey)));
        return immutable;
    }

    private boolean isInvalidIdentifier(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains("ORA-00904")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeWsKey(String wsKey) {
        return trimToEmpty(wsKey).toLowerCase(Locale.ROOT);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Configuracion de web service en pasarela.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador de servicio
     * @param enabled indica si esta habilitado
     * @param connectionType tipo de conexion (REST, SOAP, etc.)
     * @param method metodo HTTP
     * @param requestType tipo de request (JSON, PARAMETROS, etc.)
     * @param uri URI base del servicio
     */
    public record WebServiceConfig(
            Integer providerCode,
            String wsKey,
            boolean enabled,
            String connectionType,
            String method,
            String requestType,
            String uri) {
    }
}
