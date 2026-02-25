package com.femsa.gpf.pagosdigitales.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "payment_provider_code requerido")
    private Integer payment_provider_code;
    @NotBlank(message = "operation_id requerido")
    private String operation_id;
    private String request_datetime;
}
