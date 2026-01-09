package com.femsa.gpf.pagosdigitales.application.service;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * Registro de una notificacion de SafetyPay para auditoria e idempotencia.
 */
@Data
public class SafetypayNotificationRecord {

    /**
     * Codigo numerico del proveedor de pago.
     */
    private Integer paymentProviderCode;

    /**
     * Nombre del proveedor de pago.
     */
    private String providerName;

    /**
     * Identificador de venta del comercio.
     */
    private String merchantSalesId;

    /**
     * Referencia del comercio.
     */
    private String referenceNo;

    /**
     * Referencia de pago.
     */
    private String paymentReferenceNo;

    /**
     * Estado reportado por SafetyPay.
     */
    private String status;

    /**
     * Monto reportado.
     */
    private String amount;

    /**
     * Moneda reportada.
     */
    private String currencyId;

    /**
     * Firma recibida en la notificacion.
     */
    private String signature;

    /**
     * Payload original serializado.
     */
    private String rawPayload;

    /**
     * Fecha/hora de recepcion.
     */
    private OffsetDateTime receivedAt;

    /**
     * Numero de orden asociado.
     */
    private String orderNo;
}
