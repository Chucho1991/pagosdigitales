package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta dinamica para merchant-events.
 */
@Component
public class DynamicMerchantEventsRoute extends RouteBuilder {

    private final MerchantEventsProperties props;
    private final ObjectMapper objectMapper;
    private final ProviderHeaderService providerHeaderService;

    /**
     * Crea la ruta con la configuracion y el serializador.
     *
     * @param props propiedades de eventos del comercio
     * @param objectMapper serializador de payloads
     * @param providerHeaderService servicio de headers por proveedor
     */
    public DynamicMerchantEventsRoute(MerchantEventsProperties props, ObjectMapper objectMapper,
            ProviderHeaderService providerHeaderService) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.providerHeaderService = providerHeaderService;
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
                    Integer providerCode = exchange.getIn().getHeader("payment_provider_code", Integer.class);

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

                    var providerHeaders = providerHeaderService.getHeadersByProviderCode(providerCode);
                    if (providerHeaders.isEmpty()) {
                        throw new IllegalArgumentException(
                                "No hay headers configurados para CODIGO_BILLETERA: " + providerCode);
                    }
                    providerHeaders.forEach(exchange.getIn()::setHeader);

                    Object body = exchange.getIn().getBody();
                    if (body != null && !(body instanceof String)) {
                        exchange.getIn().setBody(objectMapper.writeValueAsString(body));
                    }
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
