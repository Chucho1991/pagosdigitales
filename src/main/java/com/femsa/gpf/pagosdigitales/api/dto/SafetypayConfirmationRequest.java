package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * DTO para notificaciones de confirmacion de SafetyPay (form-urlencoded).
 */
@Data
public class SafetypayConfirmationRequest {

    private String channel_POS;
    private Integer payment_provider_code;
    private String apiKey;
    private String requestDateTime;
    private String merchantSalesId;
    private String referenceNo;
    private String creationDateTime;
    private String amount;
    private String currencyId;
    private String paymentReferenceNo;
    private String status;
    private String signature;
}
