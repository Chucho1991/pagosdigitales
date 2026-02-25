package com.femsa.gpf.pagosdigitales.infrastructure.logging;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Iterator;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para persistir auditoria de consumos internos y externos.
 */
@Log4j2
@Service
public class IntegrationLogService {

    static {
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
    }

    private static final String INSERT_APP_LOG = """
            INSERT INTO TUKUNAFUNC.IN_LOGS_APP_PAG_DIGIT (
                request, response, usuario, fecha_registro, mensaje, origen, pais, canal,
                codigo_prov_pago, nombre_farmacia, folio, farmacia, cadena, pos, url, metodo,
                cp_var1, cp_var2, cp_var3, cp_number1, cp_number2, cp_number3, cp_date1, cp_date2, cp_date3
            ) VALUES (
                ?, ?, ?, SYSDATE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String INSERT_EXT_LOG = """
            INSERT INTO TUKUNAFUNC.IN_LOGS_WS_EXT (
                request, response, usuario, fecha_registro, mensaje, origen, pais, canal,
                codigo_prov_pago, nombre_farmacia, folio, farmacia, cadena, pos, url, metodo,
                cp_var1, cp_var2, cp_var3, cp_number1, cp_number2, cp_number3, cp_date1, cp_date2, cp_date3
            ) VALUES (
                ?, ?, ?, SYSDATE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private final ObjectMapper objectMapper;
    private final DatabaseExecutor databaseExecutor;

    /**
     * Crea el servicio con conexion y serializador.
     *
     * @param objectMapper serializador JSON
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public IntegrationLogService(ObjectMapper objectMapper, DatabaseExecutor databaseExecutor) {
        this.objectMapper = objectMapper;
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Guarda auditoria de servicios expuestos por la API.
     *
     * @param record datos de log
     */
    @Async("loggingTaskExecutor")
    public void logInternal(IntegrationLogRecord record) {
        insert(INSERT_APP_LOG, record, "IN_LOGS_APP_PAG_DIGIT", false);
    }

    /**
     * Guarda auditoria de servicios consumidos en proveedores externos.
     *
     * @param record datos de log
     */
    @Async("loggingTaskExecutor")
    public void logExternal(IntegrationLogRecord record) {
        insert(INSERT_EXT_LOG, record, "IN_LOGS_WS_EXT", true);
    }

    private void insert(String sql, IntegrationLogRecord record, String tableName, boolean externalLog) {
        if (record == null) {
            return;
        }
        DerivedLogValues derivedValues = deriveLogValues(record);

        try {
            databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, toJson(record.getRequestPayload()));
                    ps.setString(2, toJson(record.getResponsePayload()));
                    ps.setString(3, trim(record.getUsuario(), 100, "SYSTEM"));
                    ps.setString(4, trim(record.getMensaje(), 2000, null));
                    ps.setString(5, trim(record.getOrigen(), 100, null));
                    ps.setString(6, trim(record.getPais(), 100, null));
                    ps.setString(7, trim(record.getCanal(), 100, null));
                    ps.setString(8, trim(record.getCodigoProvPago(), 50, null));
                    ps.setString(9, trim(record.getNombreFarmacia(), 100, null));
                    ps.setString(10, trim(derivedValues.folio(), 100, null));
                    setInteger(ps, 11, record.getFarmacia());
                    setInteger(ps, 12, record.getCadena());
                    setInteger(ps, 13, record.getPos());
                    ps.setString(14, trim(record.getUrl(), 300, null));
                    ps.setString(15, trim(record.getMetodo(), 20, null));
                    ps.setString(16, trim(record.getCpVar1(), 1500, null));
                    ps.setString(17, trim(derivedValues.operationId(), 1500, null));
                    ps.setString(18, trim(externalLog ? null : record.getCpVar3(), 1500, null));
                    setInteger(ps, 19, record.getCpNumber1());
                    setInteger(ps, 20, record.getCpNumber2());
                    setInteger(ps, 21, record.getCpNumber3());
                    setDate(ps, 22, record.getCpDate1());
                    setDate(ps, 23, record.getCpDate2());
                    setDate(ps, 24, record.getCpDate3());
                    ps.executeUpdate();
                }
            });
        } catch (Exception e) {
            log.error("No fue posible guardar log en {}: {}", tableName, e.getMessage());
        }
    }

    private DerivedLogValues deriveLogValues(IntegrationLogRecord record) {
        String operationId = firstNonBlank(
                findFirstValue(record.getRequestPayload(), "operation_id", "operationId", "operationid"),
                findFirstValue(record.getResponsePayload(), "operation_id", "operationId", "operationid"));
        String merchantSalesId = firstNonBlank(
                findFirstValue(record.getRequestPayload(), "merchant_sales_id", "merchantSalesId", "MerchantSalesID",
                        "merchantsalesid"),
                findFirstValue(record.getResponsePayload(), "merchant_sales_id", "merchantSalesId", "MerchantSalesID",
                        "merchantsalesid"));
        return new DerivedLogValues(operationId, merchantSalesId);
    }

    private String findFirstValue(Object payload, String... candidateKeys) {
        JsonNode root = toJsonNode(payload);
        if (root == null || root.isNull()) {
            return null;
        }
        return findFirstValueRecursive(root, candidateKeys);
    }

    private JsonNode toJsonNode(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception e) {
                return null;
            }
        }
        if (payload instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return null;
            }
            try {
                return objectMapper.readTree(bytes);
            } catch (Exception e) {
                return null;
            }
        }
        return objectMapper.valueToTree(payload);
    }

    private String findFirstValueRecursive(JsonNode node, String... candidateKeys) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = node.get(fieldName);
                if (matchesAny(fieldName, candidateKeys) && child != null && !child.isNull()) {
                    String value = child.asText();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
                String nested = findFirstValueRecursive(child, candidateKeys);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findFirstValueRecursive(child, candidateKeys);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean matchesAny(String fieldName, String... candidateKeys) {
        String normalizedFieldName = normalizeKey(fieldName);
        for (String candidateKey : candidateKeys) {
            if (normalizedFieldName.equals(normalizeKey(candidateKey))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String text) {
            return text;
        }
        return AppUtils.formatPayload(payload, objectMapper);
    }

    private String trim(String value, int maxLength, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value;
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NUMERIC);
            return;
        }
        ps.setInt(index, value);
    }

    private void setDate(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
            return;
        }
        ps.setTimestamp(index, Timestamp.valueOf(value));
    }

    private record DerivedLogValues(String operationId, String folio) {
    }
}
