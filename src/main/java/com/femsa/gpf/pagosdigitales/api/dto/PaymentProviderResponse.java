package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * DTO con la respuesta de un proveedor de pago.
 */
@Data
public class PaymentProviderResponse {
    private String payment_provider_name;
    private Integer payment_provider_code;
    private List<BankItem> banks;
}
