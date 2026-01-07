package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Mapeos configurables para merchant-events.
 */
@Data
@Component
@ConfigurationProperties(prefix = "merchant-events")
public class MerchantEventsMappingProperties {

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

    @Data
    /**
     * Mapeos por proveedor.
     */
    public static class ProviderMapping {
        private Map<String, String> request = new HashMap<>();
        private ResponseMapping response = new ResponseMapping();
    }

    @Data
    /**
     * Mapeos de respuesta.
     */
    public static class ResponseMapping {
        private String requestId = "request_id";
        private String responseDatetime = "response_datetime";
    }
}
