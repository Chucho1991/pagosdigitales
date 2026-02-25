package com.femsa.gpf.pagosdigitales.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO de solicitud para pagos en linea directos.
 */

@Data
public class DirectOnlinePaymentRequest {

    private Integer chain;
    private Integer store;
    private String store_name;
    private Integer pos;
    private String channel_POS;
    @NotNull(message = "payment_provider_code requerido")
    private Integer payment_provider_code;
    private SalesAmount sales_amount;
    private String country_code;
    private String bank_id;
    private Boolean merchant_set_pay_amount;
    private Integer expiration_time_minutes;
    private String language_code;
    private String custom_merchant_name;
    private String merchant_sales_id;
    private String requested_payment_type;
    private String transaction_email;
    private Boolean send_email_shopper;

    /**
     * DTO con el monto de venta y la moneda.
     */
    @Data
    public static class SalesAmount {
        private BigDecimal value;
        private String currency_code;
    }
}
