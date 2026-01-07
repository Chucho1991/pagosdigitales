package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades para mapear respuestas de bancos por proveedor.
 */
@Data
@Component
@ConfigurationProperties(prefix = "getbanks")
public class BankMappingProperties {

    private Map<String, ProviderMapping> mapping = new HashMap<>();

    /**
     * Obtiene el mapeo configurado para un proveedor o el mapeo por defecto.
     *
     * @param provider nombre del proveedor.
     * @return mapeo configurado del proveedor.
     */
    public ProviderMapping resolve(String provider) {
        return mapping.getOrDefault(provider, mapping.getOrDefault("default", new ProviderMapping()));
    }

    /**
     * Mapeo por proveedor para campos de respuesta y bancos.
     */
    @Data
    public static class ProviderMapping {
        private ResponseMapping response = new ResponseMapping();
        private Map<String, String> bank = new HashMap<>();
    }

    /**
     * Mapeo de campos de respuesta para bancos.
     */
    @Data
    public static class ResponseMapping {
        private String requestId = "request_id";
        private String responseDatetime = "response_datetime";
        private String banks = "banks";
    }
}
