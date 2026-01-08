package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Mapeos configurables para la respuesta de pagos.
 */
@Data
@Component
@ConfigurationProperties(prefix = "payments")
public class PaymentsMappingProperties {

    private Map<String, ProviderMapping> mapping = new HashMap<>();

    /**
     * Resuelve el mapping por proveedor o usa default.
     *
     * @param provider nombre del proveedor
     * @return mapping del proveedor o default
     */
    public ProviderMapping resolve(String provider) {
        return mapping.getOrDefault(provider, mapping.getOrDefault("default", new ProviderMapping()));
    }

    /**
     * Mapeos por proveedor.
     */
    @Data
    public static class ProviderMapping {
        private ResponseMapping response = new ResponseMapping();
    }

    /**
     * Mapeos de respuesta.
     */
    @Data
    public static class ResponseMapping {
        private String requestId = "request_id";
        private String responseDatetime = "response_datetime";
        private String paymentOperations = "payment_operations";
    }
}
