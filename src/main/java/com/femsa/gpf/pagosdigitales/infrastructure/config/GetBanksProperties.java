package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades de configuración para consultar bancos.
 */
@Data
@Component
@ConfigurationProperties(prefix = "getbanks")
public class GetBanksProperties {

    private Map<String, ProviderConfig> providers;

    /**
     * Configuración específica por proveedor.
     */
    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String type;
        private String method;
        private String url;
        private Map<String, String> query;
        private Map<String, String> headers;
    }
}
