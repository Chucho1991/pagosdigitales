package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "getbanks")
/**
 * Propiedades de configuracion para el endpoint de bancos.
 */
public class GetBanksProperties {

    /**
     * Configuracion por proveedor.
     */
    private Map<String, ProviderConfig> providers;

    @Data
    /**
     * Configuracion de un proveedor para la consulta de bancos.
     */
    public static class ProviderConfig {
        /** Indica si el proveedor esta habilitado. */
        private boolean enabled;
        /** Tipo de integracion (por ejemplo, rest). */
        private String type;
        /** Metodo HTTP a utilizar. */
        private String method;
        /** URL base del proveedor. */
        private String url;
        /** Query params parametrizables. */
        private Map<String, String> query;
        /** Headers a enviar al proveedor. */
        private Map<String, String> headers;
    }
}
