package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * Evento de notificacion del comercio.
 */
@Data
public class MerchantEvent {

    private String event_code;
    private String creation_datetime;
    private String operation_id;
    private String merchant_sales_id;
    private String operation_status;
}
