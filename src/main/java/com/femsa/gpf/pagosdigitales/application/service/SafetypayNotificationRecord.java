package com.femsa.gpf.pagosdigitales.application.service;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * Registro de una notificacion de SafetyPay para auditoria e idempotencia.
 */
@Data
public class SafetypayNotificationRecord {

    private Integer paymentProviderCode;
    private String providerName;
    private String merchantSalesId;
    private String referenceNo;
    private String paymentReferenceNo;
    private String status;
    private String amount;
    private String currencyId;
    private String signature;
    private String rawPayload;
    private OffsetDateTime receivedAt;
    private String orderNo;
}
