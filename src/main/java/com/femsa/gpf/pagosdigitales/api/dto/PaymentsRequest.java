package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * DTO de solicitud para consultar pagos.
 */
@Data
public class PaymentsRequest {

    private Integer chain;
    private Integer store;
    private String store_name;
    private Integer pos;
    private String channel_POS;
    private Integer payment_provider_code;
    private String operation_id;
    private String request_datetime;
}
