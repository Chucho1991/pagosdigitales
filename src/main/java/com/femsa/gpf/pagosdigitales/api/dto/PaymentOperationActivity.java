package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * DTO de actividad asociada a una operacion de pago.
 */
@Data
public class PaymentOperationActivity {

    private String creation_datetime;
    private String status_code;
    private String status_description;
}
