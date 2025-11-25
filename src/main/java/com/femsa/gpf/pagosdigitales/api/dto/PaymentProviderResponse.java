package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class PaymentProviderResponse {
    private String payment_provider_name;
    private Integer payment_provider_code;
    private List<BankItem> banks;
}
