package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * DTO de respuesta para consulta de pagos.
 */
@Data
public class PaymentsResponse {

    private Integer chain;
    private Integer store;
    private Integer pos;
    private Integer payment_provider_code;
    private String request_id;
    private String response_datetime;
    private List<PaymentOperation> payment_operations;
}
