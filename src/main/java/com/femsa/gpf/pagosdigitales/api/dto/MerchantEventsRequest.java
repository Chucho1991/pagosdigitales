package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private String channel_POS;
    @NotNull(message = "payment_provider_code requerido")
    private Integer payment_provider_code;
    @Valid
    @NotEmpty(message = "merchant_events requerido")
    private List<MerchantEvent> merchant_events;
    private String request_datetime;
}
