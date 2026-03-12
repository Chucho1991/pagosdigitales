package com.femsa.gpf.pagosdigitales.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de timeout para consumos HTTP externos.
 */
@Validated
@ConfigurationProperties(prefix = "integration.external-http")
public class ExternalServiceHttpProperties {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private long timeout = DEFAULT_TIMEOUT_MS;

    /**
     * Obtiene el timeout unico aplicado a las llamadas HTTP externas.
     *
     * @return timeout configurado
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Define el timeout unico para llamadas HTTP externas.
     *
     * @param timeout timeout configurado
     * @throws IllegalArgumentException cuando el valor es cero o negativo
     */
    public void setTimeout(long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("integration.external-http.timeout debe ser mayor a cero");
        }
        this.timeout = timeout;
    }

    /**
     * Obtiene el timeout en milisegundos.
     *
     * @return timeout en ms
     */
    public long timeoutMillis() {
        return timeout;
    }

    /**
     * Construye el sufijo del endpoint Camel con los timeouts HTTP aplicables.
     *
     * @param url url base del proveedor
     * @return sufijo de query string para Camel HTTP
     */
    public String buildEndpointSuffix(String url) {
        String separator = url != null && url.contains("?") ? "&" : "?";
        long timeoutMs = timeoutMillis();
        return separator
                + "throwExceptionOnFailure=false"
                + "&connectionRequestTimeout=" + timeoutMs
                + "&connectTimeout=" + timeoutMs
                + "&responseTimeout=" + timeoutMs
                + "&soTimeout=" + timeoutMs;
    }
}
