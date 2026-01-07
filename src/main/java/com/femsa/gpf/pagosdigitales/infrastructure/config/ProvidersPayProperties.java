package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "providers-pay")
/**
 * Propiedades para mapear codigos de proveedores de pago.
 */
public class ProvidersPayProperties {

    /**
     * Mapa nombre de proveedor -> codigo numerico.
     */
    private Map<String, Integer> codes;

}
