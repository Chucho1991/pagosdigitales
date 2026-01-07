package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * DTO de respuesta para la consulta de bancos.
 */
@Data
public class BanksResponse {

    private Integer chain;
    private Integer store;
    private Integer pos;

    private String request_id;
    private String response_datetime;

    private List<PaymentProviderResponse> payment_providers;

}
