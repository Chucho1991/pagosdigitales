package com.femsa.gpf.pagosdigitales.api.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * DTO con valor y moneda para montos de pago.
 */
@Data
public class PaymentAmount {

    private BigDecimal value;
    private String currency_code;
}
