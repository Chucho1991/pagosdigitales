package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * DTO de respuesta para pagos en linea directos.
 */

@Data
public class DirectOnlinePaymentResponse {

    private Integer chain;
    private Integer store;
    private Integer pos;
    private Integer payment_provider_code;

    private String response_datetime;
    private String operation_id;
    private String bank_redirect_url;
    private String payment_expiration_datetime;
    private String payment_expiration_datetime_utc;
    private String transaction_id;

    private List<Map<String, Object>> payable_amounts;
    private List<Map<String, Object>> payment_locations;
}
