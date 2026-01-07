package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.Map;

import lombok.Data;

/**
 * DTO con el resultado por proveedor.
 */
@Data
public class ProviderItem {

    private Map.Entry<String, Integer> payment_provider;
    private Object data;
}
