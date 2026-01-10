package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsProperties;

/**
 * Ruta dinamica para merchant-events.
 */
@Component
public class DynamicMerchantEventsRoute extends RouteBuilder {

    private final MerchantEventsProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Crea la ruta con la configuracion y el serializador.
     *
     * @param props propiedades de eventos del comercio
     * @param objectMapper serializador de payloads
     */
    public DynamicMerchantEventsRoute(MerchantEventsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Configura la ruta dinamica para eventos de comercio.
     */
    @Override
    public void configure() {
        from("direct:merchant-events")
                .routeId("dynamic-merchant-events-route")
                .process(exchange -> {
                    String proveedor = exchange.getIn().getHeader("merchant-events", String.class);

                    var cfg = props.getProviders().get(proveedor);
                    if (cfg == null || !cfg.isEnabled()) {
                        throw new IllegalArgumentException("Proveedor no habilitado: " + proveedor);
                    }

                    String url = cfg.getUrl();
                    exchange.setProperty("url", url);
                    exchange.setProperty("httpMethod", cfg.getMethod());
                    exchange.setProperty("endpointSuffix", url.contains("?")
                            ? "&throwExceptionOnFailure=false"
                            : "?throwExceptionOnFailure=false");

                    if (cfg.getHeaders() != null) {
                        cfg.getHeaders().forEach((headerName, headerValue) -> {
                            exchange.getIn().setHeader(headerName, headerValue);
                        });
                    }

                    Object body = exchange.getIn().getBody();
                    if (body != null && !(body instanceof String)) {
                        exchange.getIn().setBody(objectMapper.writeValueAsString(body));
                    }
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
