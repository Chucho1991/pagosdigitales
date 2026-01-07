package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * Response generico para notificaciones de eventos del comercio.
 */
@Data
public class MerchantEventsResponse {

    private Integer chain;
    private Integer store;
    private Integer pos;
    private Integer payment_provider_code;
    private String request_id;
    private String response_datetime;
}
