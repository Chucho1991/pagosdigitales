package com.femsa.gpf.pagosdigitales.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO de solicitud para notificaciones de pago de Deuna (webhook).
 *
 * <p>Deuna envia este payload cuando un pago se completa exitosamente.
 * Solo notifica pagos exitosos de transacciones de compras.</p>
 */
@Data
public class DeunaConfirmationRequest {

    /**
     * Estado de la transaccion. "SUCCESS" indica pago exitoso.
     */
    @NotBlank(message = "status requerido")
    private String status;

    /**
     * Monto de la transaccion. Numero decimal separado por punto.
     */
    @NotNull(message = "amount requerido")
    private BigDecimal amount;

    /**
     * ID unico de la transaccion emitido por Deuna (uuid-v4, max 36 caracteres).
     */
    @NotBlank(message = "idTransaction requerido")
    private String idTransaction;

    /**
     * Codigo unico generado por el negocio para identificar la transaccion (max 20 caracteres).
     */
    private String internalTransactionReference;

    /**
     * Codigo unico que identifica la transaccion para consultas y devoluciones (12 caracteres).
     */
    private String transferNumber;

    /**
     * Fecha y hora de la transaccion (ej: "6/24/2024, 4:10:58 PM").
     */
    private String date;

    /**
     * ID de la sucursal donde se efectuo la transaccion.
     */
    private String branchId;

    /**
     * Numero de la caja donde se efectuo la transaccion.
     */
    private String posId;

    /**
     * Moneda de la transaccion (ej: "USD").
     */
    private String currency;

    /**
     * Descripcion textual del concepto de pago.
     */
    private String description;

    /**
     * Numero de identificacion del comprador.
     */
    private String customerIdentification;

    /**
     * Nombre completo del ordenante que realizo la compra.
     */
    private String customerFullName;
}
