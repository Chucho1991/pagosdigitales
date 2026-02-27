package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver definiciones de request por proveedor y ws_key.
 */
@Log4j2
@Service
public class GatewayWebServiceDefinitionService {

    private static final String SELECT_WS_DEFS = "SELECT WS.CODIGO_BILLETERA, WS.WS_KEY, D.DEFAULT_CLAVE, "
            + "D.DEFAULT_VALOR_TEXTO, D.DEFAULT_VALOR_NUM, D.DEFAULT_VALOR_FECHA, D.TIPO_DEF, "
            + "D.DEFAULT_VALOR_SISTEMA "
            + "FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS D "
            + "JOIN TUKUNAFUNC.IN_PASARELA_WS WS ON WS.ID_WS = D.ID_WS "
            + "ORDER BY WS.CODIGO_BILLETERA, WS.WS_KEY, D.ID_DEFAULT";
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<Integer, Map<String, Map<String, WsDefinition>>> definitionsByProvider = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public GatewayWebServiceDefinitionService(DatabaseExecutor databaseExecutor) {
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
     * Refresca la cache de definiciones cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<Integer, Map<String, Map<String, WsDefinition>>> loaded = loadDefinitionsFromDb();
            this.definitionsByProvider = toUnmodifiableMap(loaded);
            log.info("Cache de IN_PASARELA_WS_DEFS actualizada. Proveedores con defs: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache IN_PASARELA_WS_DEFS. Se conserva cache anterior.", e);
        }
    }

    /**
     * Resuelve parametros de query para un proveedor y ws_key.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del web service
     * @param runtimeValues valores del request para placeholders de sistema
     * @return query params resueltos en orden de definicion
     */
    public Map<String, String> getQueryParams(Integer providerCode, String wsKey, Map<String, Object> runtimeValues) {
        return resolveDefinitions(providerCode, wsKey, "QUERY", runtimeValues).entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString()),
                        LinkedHashMap::putAll);
    }

    /**
     * Resuelve defaults de payload para un proveedor y ws_key.
     *
     * @param providerCode codigo de billetera
     * @param wsKey identificador del web service
     * @param runtimeValues valores del request para placeholders de sistema
     * @return defaults resueltos en orden de definicion
     */
    public Map<String, Object> getDefaults(Integer providerCode, String wsKey, Map<String, Object> runtimeValues) {
        return resolveDefinitions(providerCode, wsKey, "DEFAULTS", runtimeValues);
    }

    private Map<String, Object> resolveDefinitions(Integer providerCode, String wsKey, String type,
            Map<String, Object> runtimeValues) {
        if (providerCode == null || wsKey == null || wsKey.isBlank() || type == null || type.isBlank()) {
            return Map.of();
        }
        Map<String, Map<String, WsDefinition>> byWsKey = definitionsByProvider.get(providerCode);
        if (byWsKey == null) {
            return Map.of();
        }
        Map<String, WsDefinition> byDefinition = byWsKey.get(normalizeWsKey(wsKey));
        if (byDefinition == null || byDefinition.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        byDefinition.values().forEach(definition -> {
            if (!type.equalsIgnoreCase(definition.type())) {
                return;
            }
            Object value = resolveValue(definition, runtimeValues);
            if (value != null) {
                resolved.put(definition.key(), value);
            }
        });
        return toUnmodifiableMap(resolved);
    }

    private Object resolveValue(WsDefinition definition, Map<String, Object> runtimeValues) {
        if (definition.systemValue() != null && !definition.systemValue().isBlank()) {
            String key = definition.systemValue().trim();
            Object runtime = runtimeValues == null ? null : runtimeValues.get(key);
            if (runtime != null) {
                return runtime;
            }
            if ("now".equalsIgnoreCase(key)) {
                return LocalDateTime.now().format(DATE_TIME_FORMAT);
            }
        }
        return definition.defaultValue();
    }

    private Map<Integer, Map<String, Map<String, WsDefinition>>> loadDefinitionsFromDb() throws Exception {
        Map<Integer, Map<String, Map<String, WsDefinition>>> temp = new HashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_WS_DEFS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int providerCode = rs.getInt("CODIGO_BILLETERA");
                    if (rs.wasNull()) {
                        continue;
                    }
                    String wsKey = normalizeWsKey(rs.getString("WS_KEY"));
                    String defaultKey = trimToEmpty(rs.getString("DEFAULT_CLAVE"));
                    if (wsKey.isBlank() || defaultKey.isBlank()) {
                        continue;
                    }
                    String type = trimToEmpty(rs.getString("TIPO_DEF")).toUpperCase(Locale.ROOT);
                    if (type.isBlank()) {
                        continue;
                    }
                    Object defaultValue = readDefaultValue(rs);
                    WsDefinition definition = new WsDefinition(
                            defaultKey,
                            defaultValue,
                            type,
                            trimToEmpty(rs.getString("DEFAULT_VALOR_SISTEMA")));
                    temp.computeIfAbsent(providerCode, value -> new LinkedHashMap<>())
                            .computeIfAbsent(wsKey, value -> new LinkedHashMap<>())
                            .put(defaultKey, definition);
                }
            }
        });

        Map<Integer, Map<String, Map<String, WsDefinition>>> immutable = new HashMap<>();
        temp.forEach((providerCode, byWsKey) -> {
            Map<String, Map<String, WsDefinition>> wsMap = new LinkedHashMap<>();
            byWsKey.forEach((wsKey, byDefinition) -> wsMap.put(wsKey, toUnmodifiableMap(byDefinition)));
            immutable.put(providerCode, toUnmodifiableMap(wsMap));
        });
        return immutable;
    }

    private Object readDefaultValue(ResultSet rs) throws Exception {
        String text = rs.getString("DEFAULT_VALOR_TEXTO");
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        BigDecimal number = rs.getBigDecimal("DEFAULT_VALOR_NUM");
        if (number != null) {
            return number.stripTrailingZeros().toPlainString();
        }
        Timestamp timestamp = rs.getTimestamp("DEFAULT_VALOR_FECHA");
        if (timestamp != null) {
            return timestamp.toLocalDateTime().format(DATE_TIME_FORMAT);
        }
        return null;
    }

    private String normalizeWsKey(String wsKey) {
        return trimToEmpty(wsKey).toLowerCase(Locale.ROOT);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private <K, V> Map<K, V> toUnmodifiableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * Definicion de request para un web service.
     *
     * @param key clave de definicion
     * @param defaultValue valor por defecto
     * @param type tipo de definicion (DEFAULTS/QUERY)
     * @param systemValue referencia a valor de sistema
     */
    public record WsDefinition(
            String key,
            Object defaultValue,
            String type,
            String systemValue) {
    }
}
