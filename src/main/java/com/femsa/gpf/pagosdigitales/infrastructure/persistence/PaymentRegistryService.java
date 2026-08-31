package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.application.ports.out.GeneratedPaymentRegistryPort;
import com.femsa.gpf.pagosdigitales.api.dto.DeunaConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEvent;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperationActivity;
import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationRequest;
import com.femsa.gpf.pagosdigitales.domain.model.GeneratedPayment;
import com.femsa.gpf.pagosdigitales.domain.model.PaymentStatus;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para registrar y actualizar pagos en IN_REGISTRO_PAGOS.
 */
@Log4j2
@Service
public class PaymentRegistryService implements GeneratedPaymentRegistryPort {

    private static final String INSERT_GENERATED_PAYMENT = """
            INSERT INTO TUKUNAFUNC.IN_REGISTRO_PAGOS (
                CADENA, FARMACIA, NOMBRE_FARMACIA, POS,
                FECHA_REGISTRO, CANAL, CODIGO_PROV_PAGO,
                FOLIO, ID_OPERACION_EXTERNO, ID_INTERNO_VENTA,
                MONTO, MONEDA, COD_ESTADO_PAGO,
                CP_VAR1, CP_NUMBER1
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String INSERT_MERCHANT_EVENT = """
            INSERT INTO TUKUNAFUNC.IN_REGISTRO_PAGOS (
                CADENA, FARMACIA, NOMBRE_FARMACIA, POS,
                FECHA_REGISTRO, CANAL, CODIGO_PROV_PAGO,
                FOLIO, ID_OPERACION_EXTERNO, ID_INTERNO_VENTA,
                COD_ESTADO_PAGO, CP_VAR1, CP_NUMBER1
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String UPDATE_MERCHANT_EVENT = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET CADENA = ?,
                FARMACIA = ?,
                NOMBRE_FARMACIA = ?,
                POS = ?,
                FECHA_REGISTRO = NVL(FECHA_REGISTRO, ?),
                CANAL = ?,
                CODIGO_PROV_PAGO = ?,
                FOLIO = ?,
                ID_INTERNO_VENTA = ?,
                COD_ESTADO_PAGO = CASE
                    WHEN COD_ESTADO_PAGO IN ('100', '102', '104') AND ? = '101'
                    THEN COD_ESTADO_PAGO
                    ELSE ?
                END,
                CP_VAR1 = ?,
                CP_NUMBER1 = ?
            WHERE ID_OPERACION_EXTERNO = ?
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
                FIRMA = ?,
                CP_VAR1 = ?,
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
                  AND (
                      NVL(ID_INTERNO_VENTA, ' ') <> NVL(?, ' ')
                      OR NVL(FARMACIA, -1) <> NVL(?, -1)
                  )
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
                  AND NVL(ID_OPERACION_EXTERNO, ' ') <> NVL(?, ' ')
            )
            WHERE ROWNUM = 1
            """;

    private static final String SELECT_EVENT_PAIR_EXISTS = """
            SELECT 1
            FROM (
                SELECT 1
                FROM TUKUNAFUNC.IN_REGISTRO_PAGOS
                WHERE ID_INTERNO_VENTA = ?
                  AND ID_OPERACION_EXTERNO = ?
                  AND NVL(FARMACIA, -1) = NVL(?, -1)
            )
            WHERE ROWNUM = 1
            """;

    private static final String UPDATE_JEP_CONFIRMATION = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET FECHA_AUTORIZACION_PROV = ?,
                NO_REFERENCIA = ?,
                NO_REFERENCIA_PAGO = ?,
                COD_ESTADO_PAGO = ?,
                CP_VAR1 = ?,
                CP_NUMBER1 = ?
            WHERE ID_OPERACION_EXTERNO = ?
            """;

    private static final String UPDATE_DEUNA_CONFIRMATION = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET FECHA_AUTORIZACION_PROV = ?,
                NO_REFERENCIA = ?,
                NO_REFERENCIA_PAGO = ?,
                MONTO = ?,
                MONEDA = ?,
                COD_ESTADO_PAGO = ?,
                CP_VAR1 = ?,
                CP_NUMBER1 = ?
            WHERE ID_OPERACION_EXTERNO = ?
              AND CODIGO_PROV_PAGO = ?
            """;

    private static final String UPDATE_DEUNA_PAYMENT_STATUS = """
            UPDATE TUKUNAFUNC.IN_REGISTRO_PAGOS
            SET FECHA_AUTORIZACION_PROV = NVL(?, FECHA_AUTORIZACION_PROV),
                NO_REFERENCIA = NVL(?, NO_REFERENCIA),
                NO_REFERENCIA_PAGO = NVL(?, NO_REFERENCIA_PAGO),
                MONTO = CASE WHEN ? IS NOT NULL AND ? > 0 THEN ? ELSE MONTO END,
                MONEDA = NVL(?, MONEDA),
                COD_ESTADO_PAGO = ?,
                CP_VAR1 = ?,
                CP_NUMBER1 = 0
            WHERE ID_OPERACION_EXTERNO = ?
              AND CODIGO_PROV_PAGO = ?
              AND NOT (
                    COD_ESTADO_PAGO IN ('100', '102', '104')
                    AND ? = '101'
              )
            """;

    private static final String SELECT_PAYMENT_STATUS = """
            SELECT *
            FROM (
                SELECT FECHA_REGISTRO,
                       FECHA_AUTORIZACION_PROV,
                       FOLIO,
                       ID_OPERACION_EXTERNO,
                       ID_INTERNO_VENTA,
                       NO_REFERENCIA,
                       MONTO,
                       MONEDA,
                       COD_ESTADO_PAGO,
                       CP_VAR1
                FROM TUKUNAFUNC.IN_REGISTRO_PAGOS
                WHERE ID_OPERACION_EXTERNO = ?
                  AND CODIGO_PROV_PAGO = ?
                ORDER BY NVL(FECHA_AUTORIZACION_PROV, FECHA_REGISTRO) DESC
            )
            WHERE ROWNUM = 1
            """;

    private final DatabaseExecutor databaseExecutor;

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public PaymentRegistryService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Registra una forma de pago generada por direct-online-payment-requests.
     *
     * @param payment datos normalizados de la forma de pago
     */
    @Override
    public void save(GeneratedPayment payment) {
        if (payment == null) {
            return;
        }

        try {
            databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
                try (PreparedStatement ps = connection.prepareStatement(INSERT_GENERATED_PAYMENT)) {
                    ps.setObject(1, payment.chain());
                    ps.setObject(2, payment.store());
                    ps.setString(3, payment.storeName());
                    ps.setObject(4, payment.pos());
                    setDate(ps, 5, payment.registrationDate());
                    ps.setString(6, payment.channel());
                    ps.setString(7, payment.paymentProviderCode() == null
                            ? null
                            : payment.paymentProviderCode().toString());
                    ps.setString(8, payment.folio());
                    ps.setString(9, payment.externalOperationId());
                    ps.setString(10, payment.internalSaleId());
                    setBigDecimal(ps, 11, payment.amount());
                    ps.setString(12, payment.currency());
                    ps.setString(13, PaymentStatus.PENDING.code());
                    ps.setString(14, errorNumberDescription(0));
                    ps.setObject(15, 0, java.sql.Types.NUMERIC);
                    ps.executeUpdate();
                }
            });
        } catch (Exception e) {
            log.error("No fue posible registrar la forma de pago en IN_REGISTRO_PAGOS: {}", e.getMessage());
        }
    }

    /**
     * Actualiza el registro del mismo operation_id o inserta uno nuevo cuando no existe.
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

        String cpVar1 = errorNumberDescription(errorNumber);
        try {
            databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
                try (PreparedStatement update = connection.prepareStatement(UPDATE_MERCHANT_EVENT);
                        PreparedStatement insert = connection.prepareStatement(INSERT_MERCHANT_EVENT)) {
                    for (MerchantEvent event : events) {
                        String paymentStatus = normalizedStatusCode(event.getOperation_status(),
                                PaymentStatus.PENDING);
                        update.setObject(1, req.getChain());
                        update.setObject(2, req.getStore());
                        update.setString(3, req.getStore_name());
                        update.setObject(4, req.getPos());
                        setDate(update, 5, parseDateTime(event.getCreation_datetime()));
                        update.setString(6, req.getChannel_POS());
                        update.setString(7,
                                req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString());
                        update.setString(8, event.getMerchant_sales_id());
                        update.setString(9, event.getMerchant_sales_id());
                        update.setString(10, paymentStatus);
                        update.setString(11, paymentStatus);
                        update.setString(12, cpVar1);
                        update.setObject(13, errorNumber, java.sql.Types.NUMERIC);
                        update.setString(14, event.getOperation_id());

                        if (update.executeUpdate() > 0) {
                            continue;
                        }

                        insert.setObject(1, req.getChain());
                        insert.setObject(2, req.getStore());
                        insert.setString(3, req.getStore_name());
                        insert.setObject(4, req.getPos());
                        setDate(insert, 5, parseDateTime(event.getCreation_datetime()));
                        insert.setString(6, req.getChannel_POS());
                        insert.setString(7,
                                req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString());
                        insert.setString(8, event.getMerchant_sales_id());
                        insert.setString(9, event.getOperation_id());
                        insert.setString(10, event.getMerchant_sales_id());
                        insert.setString(11, paymentStatus);
                        insert.setString(12, cpVar1);
                        insert.setObject(13, errorNumber, java.sql.Types.NUMERIC);
                        insert.executeUpdate();
                    }
                }
            });
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

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SELECT_OPERATION_CONFLICT)) {
                    for (MerchantEvent event : req.getMerchant_events()) {
                        if (event == null || isBlank(event.getOperation_id())) {
                            continue;
                        }
                        ps.setString(1, event.getOperation_id());
                        ps.setString(2, event.getMerchant_sales_id());
                        ps.setObject(3, req.getStore());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return "operation_id " + event.getOperation_id()
                                        + " ya existe en IN_REGISTRO_PAGOS";
                            }
                        }
                    }
                    return null;
                }
            });
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

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SELECT_FOLIO_EXISTS)) {
                    for (MerchantEvent event : req.getMerchant_events()) {
                        if (event == null || isBlank(event.getMerchant_sales_id())) {
                            continue;
                        }
                        ps.setString(1, event.getMerchant_sales_id());
                        ps.setObject(2, req.getStore());
                        ps.setString(3, event.getOperation_id());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return "folio " + event.getMerchant_sales_id()
                                        + " ya existe para la farmacia " + req.getStore();
                            }
                        }
                    }
                    return null;
                }
            });
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
    public boolean updateFromSafetypayConfirmation(SafetypayConfirmationRequest req, int errorNumber) {
        if (req == null || isBlank(req.getMerchantSalesId()) || isBlank(req.getReferenceNo())) {
            return false;
        }

        String idInternoVenta = req.getMerchantSalesId();
        String idOperacionExterno = req.getReferenceNo();
        String cpVar1 = errorNumberDescription(errorNumber);

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_CONFIRMATION)) {
                    setDate(ps, 1, parseDateTime(req.getRequestDateTime()));
                    ps.setString(2, req.getReferenceNo());
                    ps.setString(3, req.getPaymentReferenceNo());
                    setAmount(ps, 4, req.getAmount());
                    ps.setString(5, req.getCurrencyId());
                    ps.setString(6, req.getStatus());
                    ps.setString(7, req.getSignature());
                    ps.setString(8, cpVar1);
                    ps.setObject(9, errorNumber, java.sql.Types.NUMERIC);
                    ps.setString(10, idInternoVenta);
                    ps.setString(11, idOperacionExterno);
                    return ps.executeUpdate() > 0;
                }
            });
        } catch (Exception e) {
            log.error("No fue posible actualizar confirmacion SafetyPay en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el registro de pago asociado a una confirmacion de JEPFaster.
     *
     * <p>Busca por ID_OPERACION_EXTERNO (idtransaccion del QR generado) y actualiza
     * la fecha de autorizacion, numero de comprobante y estado.</p>
     *
     * @param req request de confirmacion JEP
     * @return true cuando se encontro y actualizo el registro; false en caso contrario
     */
    public boolean updateFromJepConfirmation(JepConfirmationRequest req) {
        if (req == null || isBlank(req.getIdtransaccion())) {
            return false;
        }

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_JEP_CONFIRMATION)) {
                    setDate(ps, 1, LocalDateTime.now());
                    ps.setString(2, req.getNummensaje());
                    ps.setString(3, req.getNummensaje());
                    ps.setString(4, PaymentStatus.PAID.code());
                    ps.setString(5, "JEP_CONFIRMATION_OK");
                    ps.setObject(6, 0, java.sql.Types.NUMERIC);
                    ps.setString(7, req.getIdtransaccion());
                    return ps.executeUpdate() > 0;
                }
            });
        } catch (Exception e) {
            log.error("No fue posible actualizar confirmacion JEPFaster en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si todos los eventos del request ya fueron registrados por la llave
     * ID_INTERNO_VENTA + ID_OPERACION_EXTERNO + FARMACIA.
     *
     * @param req request de merchant-events
     * @return true cuando todos los eventos ya existen en la tabla
     */
    public boolean areAllEventsAlreadyRegistered(MerchantEventsRequest req) {
        if (req == null || req.getMerchant_events() == null || req.getMerchant_events().isEmpty()) {
            return false;
        }
        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SELECT_EVENT_PAIR_EXISTS)) {
                    for (MerchantEvent event : req.getMerchant_events()) {
                        if (event == null || isBlank(event.getMerchant_sales_id()) || isBlank(event.getOperation_id())) {
                            return false;
                        }
                        ps.setString(1, event.getMerchant_sales_id());
                        ps.setString(2, event.getOperation_id());
                        ps.setObject(3, req.getStore());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            log.error("No fue posible validar idempotencia de merchant-events en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si existe registro objetivo para confirmation.
     *
     * @param req request de confirmation
     * @return true si existe al menos un registro para MerchantSalesID + ReferenceNo
     */
    public boolean existsConfirmationTarget(SafetypayConfirmationRequest req) {
        if (req == null || isBlank(req.getMerchantSalesId()) || isBlank(req.getReferenceNo())) {
            return false;
        }
        try {
            return databaseExecutor.withConnection(
                    (DatabaseExecutor.ConnectionCallback<Boolean>) connection -> existsTargetRecord(connection,
                            req.getMerchantSalesId(), req.getReferenceNo()));
        } catch (Exception e) {
            log.error("No fue posible validar existencia para confirmation en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el registro de pago asociado a una confirmacion Deuna.
     *
     * <p>Busca por ID_OPERACION_EXTERNO (idTransaction de Deuna) y actualiza
     * la fecha de autorizacion, numero de transferencia, monto, moneda y estado.</p>
     *
     * @param req request de confirmacion Deuna
     * @return true cuando se encontro y actualizo el registro; false en caso contrario
     */
    public boolean updateFromDeunaConfirmation(DeunaConfirmationRequest req) {
        if (req == null || isBlank(req.getIdTransaction())) {
            return false;
        }

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_DEUNA_CONFIRMATION)) {
                    setDate(ps, 1, firstNonNullDateTime(req.getDate(), null, LocalDateTime.now()));
                    ps.setString(2, req.getTransferNumber());
                    ps.setString(3, req.getTransferNumber());
                    if (req.getAmount() != null) {
                        ps.setBigDecimal(4, req.getAmount());
                    } else {
                        ps.setNull(4, java.sql.Types.NUMERIC);
                    }
                    ps.setString(5, req.getCurrency());
                    ps.setString(6, normalizedStatusCode(req.getStatus(), PaymentStatus.PAID));
                    ps.setString(7, "DEUNA_CONFIRMATION_OK");
                    ps.setObject(8, 0, java.sql.Types.NUMERIC);
                    ps.setString(9, req.getIdTransaction());
                    ps.setString(10, "300002");
                    return ps.executeUpdate() > 0;
                }
            });
        } catch (Exception e) {
            log.error("No fue posible actualizar confirmacion Deuna en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sincroniza en IN_REGISTRO_PAGOS el estado devuelto por payments de DEUNA.
     *
     * @param operation operacion normalizada desde la respuesta externa
     * @param providerCode codigo del proveedor DEUNA
     * @return true cuando el registro fue actualizado
     */
    public boolean synchronizeDeunaPaymentStatus(PaymentOperation operation, Integer providerCode) {
        if (operation == null || providerCode == null || isBlank(operation.getOperation_id())) {
            return false;
        }

        PaymentOperationActivity lastActivity = lastActivity(operation);
        Optional<PaymentStatus> status = PaymentStatus.fromValue(
                lastActivity == null ? null : lastActivity.getStatus_code());
        if (status.isEmpty()) {
            return false;
        }

        LocalDateTime authorizationDatetime = status.get() == PaymentStatus.PENDING
                ? null
                : firstNonNullDateTime(
                        lastActivity == null ? null : lastActivity.getCreation_datetime(),
                        operation.getCreation_datetime(),
                        LocalDateTime.now());
        BigDecimal amount = operation.getPayment_amount() == null
                ? null
                : operation.getPayment_amount().getValue();
        String currency = operation.getPayment_amount() == null
                ? null
                : operation.getPayment_amount().getCurrency_code();

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_DEUNA_PAYMENT_STATUS)) {
                    setDate(ps, 1, authorizationDatetime);
                    ps.setString(2, operation.getPayment_reference_number());
                    ps.setString(3, operation.getPayment_reference_number());
                    setBigDecimal(ps, 4, amount);
                    setBigDecimal(ps, 5, amount);
                    setBigDecimal(ps, 6, amount);
                    ps.setString(7, currency);
                    ps.setString(8, status.get().code());
                    ps.setString(9, "DEUNA_PAYMENT_STATUS_" + status.get().code());
                    ps.setString(10, operation.getOperation_id());
                    ps.setString(11, providerCode.toString());
                    ps.setString(12, status.get().code());
                    return ps.executeUpdate() > 0;
                }
            });
        } catch (Exception e) {
            log.error("No fue posible sincronizar payments de DEUNA en IN_REGISTRO_PAGOS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Consulta el ultimo estado registrado para una operacion y proveedor.
     *
     * @param operationId identificador externo de la operacion
     * @param providerCode codigo del proveedor de pago
     * @return pago registrado cuando existe
     * @throws IllegalStateException cuando no es posible consultar la base de datos
     */
    public Optional<RegisteredPayment> findPaymentStatus(String operationId, Integer providerCode) {
        if (isBlank(operationId) || providerCode == null) {
            return Optional.empty();
        }

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(SELECT_PAYMENT_STATUS)) {
                    ps.setString(1, operationId);
                    ps.setString(2, providerCode.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }

                        Timestamp registrationTimestamp = rs.getTimestamp("FECHA_REGISTRO");
                        Timestamp authorizationTimestamp = rs.getTimestamp("FECHA_AUTORIZACION_PROV");
                        return Optional.of(new RegisteredPayment(
                                registrationTimestamp == null ? null : registrationTimestamp.toLocalDateTime(),
                                authorizationTimestamp == null ? null : authorizationTimestamp.toLocalDateTime(),
                                rs.getString("FOLIO"),
                                rs.getString("ID_OPERACION_EXTERNO"),
                                rs.getString("ID_INTERNO_VENTA"),
                                rs.getString("NO_REFERENCIA"),
                                rs.getBigDecimal("MONTO"),
                                rs.getString("MONEDA"),
                                rs.getString("COD_ESTADO_PAGO"),
                                rs.getString("CP_VAR1")));
                    }
                }
            });
        } catch (Exception e) {
            log.error("No fue posible consultar el estado del pago en IN_REGISTRO_PAGOS: {}", e.getMessage());
            throw new IllegalStateException("No fue posible consultar el estado del pago", e);
        }
    }

    /**
     * Datos normalizados de un pago almacenado en IN_REGISTRO_PAGOS.
     *
     * @param registrationDatetime fecha de registro
     * @param authorizationDatetime fecha de autorizacion del proveedor
     * @param folio folio del comercio
     * @param operationId identificador externo
     * @param internalSaleId identificador interno de venta
     * @param referenceNumber numero de referencia
     * @param amount monto registrado
     * @param currency moneda registrada
     * @param paymentStatus estado del pago
     * @param statusDetail detalle interno del resultado
     */
    public record RegisteredPayment(
            LocalDateTime registrationDatetime,
            LocalDateTime authorizationDatetime,
            String folio,
            String operationId,
            String internalSaleId,
            String referenceNumber,
            BigDecimal amount,
            String currency,
            String paymentStatus,
            String statusDetail) {
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

    private void setBigDecimal(PreparedStatement ps, int index, BigDecimal amount) throws Exception {
        if (amount == null) {
            ps.setNull(index, java.sql.Types.NUMERIC);
            return;
        }
        ps.setBigDecimal(index, amount);
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
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a", Locale.US));
        } catch (Exception ignored) {
        }
        return null;
    }

    private PaymentOperationActivity lastActivity(PaymentOperation operation) {
        List<PaymentOperationActivity> activities = operation.getOperation_activities();
        return activities == null || activities.isEmpty() ? null : activities.get(activities.size() - 1);
    }

    private LocalDateTime firstNonNullDateTime(String activityDate, String operationDate, LocalDateTime fallback) {
        LocalDateTime parsedActivityDate = parseDateTime(activityDate);
        if (parsedActivityDate != null) {
            return parsedActivityDate;
        }
        LocalDateTime parsedOperationDate = parseDateTime(operationDate);
        return parsedOperationDate == null ? fallback : parsedOperationDate;
    }

    private String normalizedStatusCode(String value, PaymentStatus fallback) {
        return PaymentStatus.fromValue(value).orElse(fallback).code();
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

}
