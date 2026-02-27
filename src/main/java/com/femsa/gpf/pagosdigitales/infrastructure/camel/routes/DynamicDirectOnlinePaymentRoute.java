package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta Camel dinamica para pagos en linea directos.
 */
@Component
public class DynamicDirectOnlinePaymentRoute extends RouteBuilder {

    private final DirectOnlinePaymentProperties props;
    private final ObjectMapper objectMapper;
    private final ProviderHeaderService providerHeaderService;

    /**
     * Crea la ruta con configuracion y serializador.
     *
     * @param props propiedades de proveedores de pago
     * @param objectMapper serializador de payloads
     * @param providerHeaderService servicio de headers por proveedor
     */
    public DynamicDirectOnlinePaymentRoute(DirectOnlinePaymentProperties props, ObjectMapper objectMapper,
            ProviderHeaderService providerHeaderService) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.providerHeaderService = providerHeaderService;
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

                    log.info("Request enviado a endpoint externo {}: {}", cfg.getUrl(), exchange.getIn().getBody());
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
