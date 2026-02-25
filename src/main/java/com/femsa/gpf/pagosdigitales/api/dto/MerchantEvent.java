package com.femsa.gpf.pagosdigitales.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Evento de notificacion del comercio.
 */
@Data
public class MerchantEvent {

    private String event_code;
    private String creation_datetime;
    private String operation_id;
    @NotBlank(message = "merchant_sales_id requerido en merchant_events")
    private String merchant_sales_id;
    private String operation_status;
}
