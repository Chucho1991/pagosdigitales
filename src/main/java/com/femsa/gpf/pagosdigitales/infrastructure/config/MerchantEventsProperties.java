package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades de configuracion para merchant-events.
 */
@Data
@Component
@ConfigurationProperties(prefix = "merchant-events")
public class MerchantEventsProperties {

    /**
     * Configuracion por proveedor.
     */
    private Map<String, ProviderConfig> providers;

    @Data
    /**
     * Configuracion de un proveedor.
     */
    public static class ProviderConfig {
        /** Indica si el proveedor esta habilitado. */
        private boolean enabled;
        /** Tipo de integracion (por ejemplo, rest). */
        private String type;
        /** Metodo HTTP a utilizar. */
        private String method;
        /** URL del servicio externo. */
        private String url;
        /** Headers a enviar al proveedor. */
        private Map<String, String> headers;
    }
}
