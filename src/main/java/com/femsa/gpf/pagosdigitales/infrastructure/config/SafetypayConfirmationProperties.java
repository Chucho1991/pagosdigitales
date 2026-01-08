package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades de configuracion para confirmaciones de SafetyPay.
 */
@Data
@Component
@ConfigurationProperties(prefix = "safetypay.confirmation")
public class SafetypayConfirmationProperties {

    /** Indica si el endpoint de confirmacion esta habilitado. */
    private boolean enabled = true;
    /** Configuracion por proveedor. */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Configuracion de un proveedor para confirmaciones.
     */
    @Data
    public static class ProviderConfig {
        /** ApiKey esperada para validar notificaciones entrantes. */
        private String apiKey;
        /** Secreto usado para calcular firmas. */
        private String secret;
        /** Modo de firma, reservado para variantes futuras. */
        private String signatureMode = "SHA256";
        /** Lista opcional de IPs permitidas. */
        private List<String> allowedIps;
    }
}
