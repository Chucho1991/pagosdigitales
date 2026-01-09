package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuracion de mapeo de errores por proveedor.
 */
@Data
@Component
@ConfigurationProperties(prefix = "error-mapping")
public class ErrorMappingProperties {

    private Map<String, ProviderMapping> providers = new HashMap<>();

    /**
     * Resuelve el mapping por proveedor o retorna el default.
     *
     * @param provider nombre del proveedor
     * @return mapping del proveedor o default
     */
    public ProviderMapping resolve(String provider) {
        return providers.getOrDefault(provider, providers.getOrDefault("default", new ProviderMapping()));
    }

    /**
     * Mapeo de error para un proveedor.
     */
    @Data
    public static class ProviderMapping {
        private String error = "error";
    }
}
