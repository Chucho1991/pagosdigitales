package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * DTO con el detalle de una operacion de pago.
 */
@Data
public class PaymentOperation {

    private List<Object> refunds_related;
    private String creation_datetime;
    private String operation_id;
    private String merchant_sales_id;
    private String merchant_order_id;
    private PaymentAmount payment_amount;
    private PaymentAmount shopper_amount;
    private String shopper_email;
    private Object additional_info;
    private List<PaymentOperationActivity> operation_activities;
    private String payment_reference_number;
}
