package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * DTO de respuesta para confirmaciones de SafetyPay.
 */
@Data
public class SafetypayConfirmationResponse {

    private String channel_POS;
    private int errorNumber;
    private String responseDateTime;
    private String merchantSalesId;
    private String referenceNo;
    private String creationDateTime;
    private String amount;
    private String currencyId;
    private String paymentReferenceNo;
    private String status;
    private String orderNo;
    private String signature;

    /**
     * Construye el CSV de salida en el orden definido por SafetyPay.
     *
     * @return linea CSV sin salto de linea
     */
    public String toCsvLine() {
        return String.join(",",
                String.valueOf(errorNumber),
                nullSafe(responseDateTime),
                nullSafe(merchantSalesId),
                nullSafe(referenceNo),
                nullSafe(creationDateTime),
                nullSafe(amount),
                nullSafe(currencyId),
                nullSafe(paymentReferenceNo),
                nullSafe(status),
                nullSafe(orderNo),
                nullSafe(signature));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
