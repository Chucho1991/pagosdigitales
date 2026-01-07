package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * Request generico para notificaciones de eventos del comercio.
 */
@Data
public class MerchantEventsRequest {

    private Integer chain;
    private Integer store;
    private String store_name;
    private Integer pos;
    private Integer payment_provider_code;
    private List<MerchantEvent> merchant_events;
    private String request_datetime;
}
