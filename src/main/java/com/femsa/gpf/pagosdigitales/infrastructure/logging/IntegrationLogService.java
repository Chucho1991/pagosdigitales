package com.femsa.gpf.pagosdigitales.infrastructure.logging;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String INSERT_EXT_LOG = """
            INSERT INTO TUKUNAFUNC.IN_LOGS_WS_EXT (
                request, response, usuario, fecha_registro, mensaje, origen, pais, canal,
                codigo_prov_pago, nombre_farmacia, folio, farmacia, cadena, pos, url, metodo,
                cp_var1, cp_var2, cp_var3, cp_number1, cp_number2, cp_number3, cp_date1, cp_date2, cp_date3
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private final ObjectMapper objectMapper;
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final Properties connectionProperties;

    /**
     * Crea el servicio con conexion y serializador.
     *
     * @param objectMapper serializador JSON
     * @param dbUrl URL JDBC
     * @param dbUsername usuario BD
     * @param dbPassword password BD
     */
    public IntegrationLogService(ObjectMapper objectMapper,
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.objectMapper = objectMapper;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
        this.connectionProperties = buildConnectionProperties(dbUsername, dbPassword);
    }

    /**
     * Guarda auditoria de servicios expuestos por la API.
     *
     * @param record datos de log
     */
    public void logInternal(IntegrationLogRecord record) {
        insert(INSERT_APP_LOG, record, "IN_LOGS_APP_PAG_DIGIT");
    }

    /**
     * Guarda auditoria de servicios consumidos en proveedores externos.
     *
     * @param record datos de log
     */
    public void logExternal(IntegrationLogRecord record) {
        insert(INSERT_EXT_LOG, record, "IN_LOGS_WS_EXT");
    }

    private void insert(String sql, IntegrationLogRecord record, String tableName) {
        if (record == null) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, toJson(record.getRequestPayload()));
            ps.setString(2, toJson(record.getResponsePayload()));
            ps.setString(3, trim(record.getUsuario(), 100, "SYSTEM"));
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(5, trim(record.getMensaje(), 2000, null));
            ps.setString(6, trim(record.getOrigen(), 100, null));
            ps.setString(7, trim(record.getPais(), 100, null));
            ps.setString(8, trim(record.getCanal(), 100, null));
            ps.setString(9, trim(record.getCodigoProvPago(), 50, null));
            ps.setString(10, trim(record.getNombreFarmacia(), 100, null));
            ps.setString(11, trim(record.getFolio(), 100, null));
            setInteger(ps, 12, record.getFarmacia());
            setInteger(ps, 13, record.getCadena());
            setInteger(ps, 14, record.getPos());
            ps.setString(15, trim(record.getUrl(), 300, null));
            ps.setString(16, trim(record.getMetodo(), 20, null));
            ps.setString(17, trim(record.getCpVar1(), 1500, null));
            ps.setString(18, trim(record.getCpVar2(), 1500, null));
            ps.setString(19, trim(record.getCpVar3(), 1500, null));
            setInteger(ps, 20, record.getCpNumber1());
            setInteger(ps, 21, record.getCpNumber2());
            setInteger(ps, 22, record.getCpNumber3());
            setDate(ps, 23, record.getCpDate1());
            setDate(ps, 24, record.getCpDate2());
            setDate(ps, 25, record.getCpDate3());

            ps.executeUpdate();
        } catch (Exception e) {
            log.error("No fue posible guardar log en {}: {}", tableName, e.getMessage());
        }
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

    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        return props;
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
}
