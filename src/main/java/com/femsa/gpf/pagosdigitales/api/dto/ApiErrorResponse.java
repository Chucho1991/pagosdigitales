package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * Respuesta generica de error para endpoints internos.
 */
@Data
public class ApiErrorResponse {

    private Integer chain;
    private Integer store;
    private String store_name;
    private Integer pos;
    private String channel_POS;
    private Integer payment_provider_code;
    private ErrorInfo error;
}
