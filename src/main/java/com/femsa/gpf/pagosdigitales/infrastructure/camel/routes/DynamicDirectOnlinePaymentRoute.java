package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentProperties;

/**
 * Ruta Camel dinamica para pagos en linea directos.
 */
@Component
public class DynamicDirectOnlinePaymentRoute extends RouteBuilder {

    private final DirectOnlinePaymentProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Crea la ruta con configuracion y serializador.
     *
     * @param props propiedades de proveedores de pago
     * @param objectMapper serializador de payloads
     */
    public DynamicDirectOnlinePaymentRoute(DirectOnlinePaymentProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Configura la ruta dinamica para llamadas a proveedores.
     */
    @Override
    public void configure() {
        from("direct:direct-online-payment-requests")
                .routeId("dynamic-direct-online-payment-requests-route")
                .process(exchange -> {
                    String proveedor = exchange.getIn().getHeader("direct-online-payment-requests", String.class);

                    var cfg = props.getProviders().get(proveedor);
                    if (cfg == null || !cfg.isEnabled()) {
                        throw new IllegalArgumentException("Proveedor no habilitado: " + proveedor);
                    }

                    exchange.setProperty("url", cfg.getUrl());
                    exchange.setProperty("httpMethod", cfg.getMethod());

                    if (cfg.getHeaders() != null) {
                        cfg.getHeaders().forEach((headerName, headerValue) -> {
                            exchange.getIn().setHeader(headerName, headerValue);
                        });
                    }

                    Object body = exchange.getIn().getBody();
                    if (body != null && !(body instanceof String)) {
                        exchange.getIn().setBody(objectMapper.writeValueAsString(body));
                    }

                    log.info("Request enviado a endpoint externo {}: {}", cfg.getUrl(), exchange.getIn().getBody());
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}");
    }
}
