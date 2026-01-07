package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * DTO de solicitud para consulta de bancos.
 */

@Data
public class BanksRequest {

    private Integer chain;
    private Integer store;
    private String store_name;
    private Integer pos;
    private Integer payment_provider_code;
    private String channel_POS;
    private String country_code;
}
