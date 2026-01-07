package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades de configuración para proveedores de pago.
 */
@Data
@Component
@ConfigurationProperties(prefix = "providers-pay")
public class ProvidersPayProperties {

    private Map<String, Integer> codes;

}
