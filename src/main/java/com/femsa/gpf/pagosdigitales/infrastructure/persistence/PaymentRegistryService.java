package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.api.dto.MerchantEvent;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationRequest;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para registrar y actualizar pagos en IN_REGISTRO_PAGOS.
 */
@Log4j2
@Service
public class PaymentRegistryService {

    private static final String INSERT_MERCHANT_EVENT = """
            INSERT INTO TUKUNAFUNC.IN_REGISTRO_PAGOS (
                CADENA, FARMACIA, NOMBRE_FARMACIA, POS,
                FECHA_REGISTRO, CANAL, CODIGO_PROV_PAGO,
                FOLIO, ID_OPERACION_EXTERNO, ID_INTERNO_VENTA,
                CP_VAR1, CP_NUMBER1
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String SELECT_CONFIRMATION_TARGET = """
            SELECT CODIGO
            FROM TUKUNAFUNC.IN_REGISTRO_PAGOS
            WHERE ID_INTERNO_VENTA = ?
              AND ID_OPERACION_EXTERNO = ?
            """;

    private static final String UPDATE_CONFIRMATION = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET FECHA_AUTORIZACION_PROV = ?,
                NO_REFERENCIA = ?,
                NO_REFERENCIA_PAGO = ?,
                MONTO = ?,
                MONEDA = ?,
                COD_ESTADO_PAGO = ?,
                FIRMA = ?
            WHERE ID_INTERNO_VENTA = ?
              AND ID_OPERACION_EXTERNO = ?
            """;

    private static final String UPDATE_CONFIRMATION_ERROR_FIELDS = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET CP_VAR1 = ?,
                CP_NUMBER1 = ?
            WHERE ID_INTERNO_VENTA = ?
              AND ID_OPERACION_EXTERNO = ?
            """;

    private static final String SELECT_OPERATION_CONFLICT = """
            SELECT ID_OPERACION_EXTERNO
            FROM (
                SELECT ID_OPERACION_EXTERNO
                FROM TUKUNAFUNC.IN_REGISTRO_PAGOS
                WHERE ID_OPERACION_EXTERNO = ?
            )
            WHERE ROWNUM = 1
            """;

    private static final String SELECT_FOLIO_EXISTS = """
            SELECT ID_INTERNO_VENTA
            FROM (
                SELECT ID_INTERNO_VENTA
                FROM TUKUNAFUNC.IN_REGISTRO_PAGOS
                WHERE NVL(ID_INTERNO_VENTA, ' ') = NVL(?, ' ')
                  AND NVL(FARMACIA, -1) = NVL(?, -1)
            )
            WHERE ROWNUM = 1
            """;

    private final String dbUrl;
    private final Properties connectionProperties;

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param dbUrl URL JDBC
     * @param dbUsername usuario BD
     * @param dbPassword password BD
     */
    public PaymentRegistryService(@Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.dbUrl = dbUrl;
        this.connectionProperties = buildConnectionProperties(dbUsername, dbPassword);
    }

    /**
     * Inserta un registro por cada evento recibido en merchant-events.
     *
     * @param req request de merchant-events
     */
    public void registerMerchantEvents(MerchantEventsRequest req, int errorNumber) {
        if (req == null) {
            return;
        }
        List<MerchantEvent> events = req.getMerchant_events();
        if (events == null || events.isEmpty()) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(INSERT_MERCHANT_EVENT)) {

            for (MerchantEvent event : events) {
                ps.setObject(1, req.getChain());
                ps.setObject(2, req.getStore());
                ps.setString(3, req.getStore_name());
                ps.setObject(4, req.getPos());
                setDate(ps, 5, parseDateTime(event.getCreation_datetime()));
                ps.setString(6, req.getChannel_POS());
                ps.setString(7, req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString());
                ps.setString(8, event.getMerchant_sales_id());
                ps.setString(9, event.getOperation_id());
                ps.setString(10, event.getMerchant_sales_id());
                ps.setNull(11, java.sql.Types.VARCHAR);
                ps.setNull(12, java.sql.Types.NUMERIC);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            log.error("No fue posible insertar registros de merchant-events en IN_REGISTRO_PAGOS: {}", e.getMessage());
        }
    }

    /**
     * Valida si algun operation_id del request ya existe en la tabla.
     *
     * @param req request de merchant-events
     * @return mensaje de conflicto o null cuando no hay conflicto
     */
    public String validateOperationIdOwnership(MerchantEventsRequest req) {
        if (req == null || req.getMerchant_events() == null || req.getMerchant_events().isEmpty()) {
            return null;
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_OPERATION_CONFLICT)) {
            for (MerchantEvent event : req.getMerchant_events()) {
                if (event == null || isBlank(event.getOperation_id())) {
                    continue;
                }
                ps.setString(1, event.getOperation_id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return "operation_id " + event.getOperation_id()
                                + " ya existe en IN_REGISTRO_PAGOS";
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("No fue posible validar ownership de operation_id en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return "No fue posible validar operation_id";
        }
    }

    /**
     * Valida si algun folio+farmacia del request ya existe en la tabla.
     *
     * @param req request de merchant-events
     * @return mensaje de conflicto o null cuando no hay conflicto
     */
    public String validateFolioUniqueness(MerchantEventsRequest req) {
        if (req == null || req.getMerchant_events() == null || req.getMerchant_events().isEmpty()) {
            return null;
        }

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_FOLIO_EXISTS)) {
            for (MerchantEvent event : req.getMerchant_events()) {
                if (event == null || isBlank(event.getMerchant_sales_id())) {
                    continue;
                }
                ps.setString(1, event.getMerchant_sales_id());
                ps.setObject(2, req.getStore());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return "folio " + event.getMerchant_sales_id()
                                + " ya existe para la farmacia " + req.getStore();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("No fue posible validar unicidad de folio en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return "No fue posible validar folio";
        }
    }

    /**
     * Actualiza el registro de pago asociado a una confirmacion SafetyPay.
     *
     * @param req request de confirmacion SafetyPay
     * @return true cuando se encontro y actualizo el registro; false en caso contrario
     */
    public boolean updateFromSafetypayConfirmation(SafetypayConfirmationRequest req) {
        if (req == null || isBlank(req.getMerchantSalesId()) || isBlank(req.getReferenceNo())) {
            return false;
        }

        String idInternoVenta = req.getMerchantSalesId();
        String idOperacionExterno = req.getReferenceNo();

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties)) {
            if (!existsTargetRecord(connection, idInternoVenta, idOperacionExterno)) {
                log.warn("No existe registro en IN_REGISTRO_PAGOS para ID_INTERNO_VENTA={} e ID_OPERACION_EXTERNO={}",
                        idInternoVenta, idOperacionExterno);
                return false;
            }

            try (PreparedStatement ps = connection.prepareStatement(UPDATE_CONFIRMATION)) {
                setDate(ps, 1, parseDateTime(req.getRequestDateTime()));
                ps.setString(2, req.getReferenceNo());
                ps.setString(3, req.getPaymentReferenceNo());
                setAmount(ps, 4, req.getAmount());
                ps.setString(5, req.getCurrencyId());
                ps.setString(6, req.getStatus());
                ps.setString(7, req.getSignature());
                ps.setString(8, idInternoVenta);
                ps.setString(9, idOperacionExterno);
                int updated = ps.executeUpdate();
                return updated > 0;
            }
        } catch (Exception e) {
            log.error("No fue posible actualizar confirmacion SafetyPay en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza CP_VAR1 y CP_NUMBER1 para confirmation segun el ErrorNumber final.
     *
     * @param req request de confirmacion
     * @param errorNumber codigo de error final
     * @return true si se actualizo al menos un registro
     */
    public boolean updateConfirmationErrorInfo(SafetypayConfirmationRequest req, int errorNumber) {
        if (req == null || isBlank(req.getMerchantSalesId()) || isBlank(req.getReferenceNo())) {
            return false;
        }

        String idInternoVenta = req.getMerchantSalesId();
        String idOperacionExterno = req.getReferenceNo();
        String cpVar1 = errorNumberDescription(errorNumber);

        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(UPDATE_CONFIRMATION_ERROR_FIELDS)) {
            ps.setString(1, cpVar1);
            ps.setObject(2, errorNumber, java.sql.Types.NUMERIC);
            ps.setString(3, idInternoVenta);
            ps.setString(4, idOperacionExterno);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("No fue posible actualizar CP_VAR1/CP_NUMBER1 de confirmation en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    private boolean existsTargetRecord(Connection connection, String idInternoVenta, String idOperacionExterno)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_CONFIRMATION_TARGET)) {
            ps.setString(1, idInternoVenta);
            ps.setString(2, idOperacionExterno);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void setDate(PreparedStatement ps, int index, LocalDateTime dateTime) throws Exception {
        if (dateTime == null) {
            ps.setNull(index, java.sql.Types.DATE);
            return;
        }
        ps.setTimestamp(index, Timestamp.valueOf(dateTime));
    }

    private void setAmount(PreparedStatement ps, int index, String amount) throws Exception {
        if (isBlank(amount)) {
            ps.setNull(index, java.sql.Types.NUMERIC);
            return;
        }
        ps.setBigDecimal(index, new BigDecimal(amount.trim()));
    }

    private LocalDateTime parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String errorNumberDescription(int errorNumber) {
        return switch (errorNumber) {
            case 0 -> "No error";
            case 1 -> "API Key not recognized";
            case 2 -> "Signature not valid";
            default -> "Other errors";
        };
    }

    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        return props;
    }
}
