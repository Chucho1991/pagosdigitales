package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver mapeos de servicios por proveedor y ws_key desde BD.
 */
@Log4j2
@Service
public class ServiceMappingConfigService {

    private static final String SELECT_SERVICE_MAPPINGS = "SELECT ID_MAPEO_SERVICIO, CODIGO_BILLETERA, "
            + "APP_SERVICE_KEY, APP_OPERATION, DIRECCION, SECCION_APP, ATRIBUTO_APP, "
            + "SECCION_EXT, ATRIBUTO_EXT, ORDEN_APLICACION, ACTIVO "
            + "FROM TUKUNAFUNC.AD_MAPEO_SERVICIOS";
    private static final String DEFAULT_OPERATION = "DEFAULT";
    private static final String DIRECTION_REQUEST = "REQUEST";
    private static final String DIRECTION_RESPONSE = "RESPONSE";
    private static final String DIRECTION_ERROR = "ERROR";
    private static final String SECTION_BODY = "BODY";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> mappingsByProvider = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public ServiceMappingConfigService(DatabaseExecutor databaseExecutor) {
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
     * Refresca la cache de mapeos cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            var loaded = loadMappingsFromDb();
            this.mappingsByProvider = toImmutable(loaded);
            log.info("Cache de AD_MAPEO_SERVICIOS actualizada. Proveedores con mapeos: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache AD_MAPEO_SERVICIOS. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene mapeos de request en seccion BODY para un servicio.
     * La clave de salida es atributo externo y el valor es atributo app.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del servicio
     * @param providerName nombre del proveedor para resolver operacion
     * @return mapa ordenado de atributo externo -> atributo app
     */
    public Map<String, String> getRequestBodyMappings(Integer providerCode, String wsKey, String providerName) {
        List<ServiceMapping> entries = resolve(providerCode, wsKey, providerName, DIRECTION_REQUEST);
        Map<String, String> result = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> SECTION_BODY.equals(entry.appSection()) && SECTION_BODY.equals(entry.externalSection()))
                .forEach(entry -> result.put(entry.externalAttribute(), entry.appAttribute()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Obtiene mapeos de response en seccion BODY para un servicio.
     * La clave de salida es atributo app y el valor es atributo externo.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del servicio
     * @param providerName nombre del proveedor para resolver operacion
     * @return mapa ordenado de atributo app -> atributo externo
     */
    public Map<String, String> getResponseBodyMappings(Integer providerCode, String wsKey, String providerName) {
        List<ServiceMapping> entries = resolve(providerCode, wsKey, providerName, DIRECTION_RESPONSE);
        Map<String, String> result = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> SECTION_BODY.equals(entry.appSection()) && SECTION_BODY.equals(entry.externalSection()))
                .forEach(entry -> result.put(entry.appAttribute(), entry.externalAttribute()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Obtiene el path del objeto de error para un servicio.
     * Busca primero el atributo app "error"; si no existe usa el primer mapeo de ERROR.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del servicio
     * @param providerName nombre del proveedor para resolver operacion
     * @return path de error; por defecto retorna "error"
     */
    public String getErrorPath(Integer providerCode, String wsKey, String providerName) {
        List<ServiceMapping> entries = resolve(providerCode, wsKey, providerName, DIRECTION_ERROR);
        if (entries.isEmpty()) {
            return "error";
        }
        for (ServiceMapping entry : entries) {
            if ("error".equalsIgnoreCase(entry.appAttribute())
                    && SECTION_BODY.equals(entry.appSection())
                    && SECTION_BODY.equals(entry.externalSection())
                    && !entry.externalAttribute().isBlank()) {
                return entry.externalAttribute();
            }
        }
        ServiceMapping first = entries.get(0);
        return first.externalAttribute().isBlank() ? "error" : first.externalAttribute();
    }

    private List<ServiceMapping> resolve(Integer providerCode, String wsKey, String providerName, String direction) {
        if (providerCode == null || wsKey == null || wsKey.isBlank()) {
            return List.of();
        }
        var byService = mappingsByProvider.get(providerCode);
        if (byService == null) {
            return List.of();
        }
        var byOperation = byService.get(normalizeWsKey(wsKey));
        if (byOperation == null) {
            return List.of();
        }
        String operationKey = normalizeOperation(providerName);
        List<ServiceMapping> specific = byOperation.getOrDefault(operationKey, Map.of())
                .getOrDefault(direction, List.of());
        if (!specific.isEmpty()) {
            return specific;
        }
        return byOperation.getOrDefault(DEFAULT_OPERATION, Map.of()).getOrDefault(direction, List.of());
    }

    private Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> loadMappingsFromDb()
            throws Exception {
        Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> temp = new HashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_SERVICE_MAPPINGS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int providerCode = rs.getInt("CODIGO_BILLETERA");
                    if (rs.wasNull()) {
                        continue;
                    }
                    if (!"S".equalsIgnoreCase(trimToEmpty(rs.getString("ACTIVO")))) {
                        continue;
                    }

                    String wsKey = normalizeWsKey(rs.getString("APP_SERVICE_KEY"));
                    String operation = normalizeOperation(rs.getString("APP_OPERATION"));
                    String direction = trimToEmpty(rs.getString("DIRECCION")).toUpperCase(Locale.ROOT);
                    String appSection = trimToEmpty(rs.getString("SECCION_APP")).toUpperCase(Locale.ROOT);
                    String appAttribute = trimToEmpty(rs.getString("ATRIBUTO_APP"));
                    String externalSection = trimToEmpty(rs.getString("SECCION_EXT")).toUpperCase(Locale.ROOT);
                    String externalAttribute = trimToEmpty(rs.getString("ATRIBUTO_EXT"));
                    int order = rs.getInt("ORDEN_APLICACION");
                    long id = rs.getLong("ID_MAPEO_SERVICIO");

                    if (wsKey.isBlank() || direction.isBlank() || appAttribute.isBlank() || externalAttribute.isBlank()) {
                        continue;
                    }

                    ServiceMapping mapping = new ServiceMapping(
                            id,
                            order,
                            appSection,
                            appAttribute,
                            externalSection,
                            externalAttribute);

                    temp.computeIfAbsent(providerCode, value -> new LinkedHashMap<>())
                            .computeIfAbsent(wsKey, value -> new LinkedHashMap<>())
                            .computeIfAbsent(operation, value -> new LinkedHashMap<>())
                            .computeIfAbsent(direction, value -> new ArrayList<>())
                            .add(mapping);
                }
            }
        });

        for (Map<String, Map<String, Map<String, List<ServiceMapping>>>> byService : temp.values()) {
            for (Map<String, Map<String, List<ServiceMapping>>> byOperation : byService.values()) {
                for (Map<String, List<ServiceMapping>> byDirection : byOperation.values()) {
                    for (List<ServiceMapping> entries : byDirection.values()) {
                        entries.sort(Comparator
                                .comparingInt(ServiceMapping::order)
                                .thenComparingLong(ServiceMapping::id));
                    }
                }
            }
        }
        return temp;
    }

    private Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> toImmutable(
            Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> source) {
        Map<Integer, Map<String, Map<String, Map<String, List<ServiceMapping>>>>> immutableProviders =
                new LinkedHashMap<>();
        source.forEach((provider, byService) -> {
            Map<String, Map<String, Map<String, List<ServiceMapping>>>> immutableServices = new LinkedHashMap<>();
            byService.forEach((service, byOperation) -> {
                Map<String, Map<String, List<ServiceMapping>>> immutableOperations = new LinkedHashMap<>();
                byOperation.forEach((operation, byDirection) -> {
                    Map<String, List<ServiceMapping>> immutableDirections = new LinkedHashMap<>();
                    byDirection.forEach((direction, entries) -> immutableDirections.put(direction, List.copyOf(entries)));
                    immutableOperations.put(operation, Collections.unmodifiableMap(immutableDirections));
                });
                immutableServices.put(service, Collections.unmodifiableMap(immutableOperations));
            });
            immutableProviders.put(provider, Collections.unmodifiableMap(immutableServices));
        });
        return Collections.unmodifiableMap(immutableProviders);
    }

    private String normalizeWsKey(String wsKey) {
        return trimToEmpty(wsKey).toLowerCase(Locale.ROOT);
    }

    private String normalizeOperation(String providerName) {
        String normalized = trimToEmpty(providerName).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.isBlank()) {
            return DEFAULT_OPERATION;
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Registro de mapeo individual de servicio.
     *
     * @param id identificador del mapeo
     * @param order orden de aplicacion
     * @param appSection seccion del campo app
     * @param appAttribute atributo app
     * @param externalSection seccion del campo externo
     * @param externalAttribute atributo externo
     */
    public record ServiceMapping(
            long id,
            int order,
            String appSection,
            String appAttribute,
            String externalSection,
            String externalAttribute) {
    }
}
