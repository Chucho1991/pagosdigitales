package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta Camel dinamica para pagos en linea directos.
 */
@Component
public class DynamicDirectOnlinePaymentRoute extends RouteBuilder {

    private final ObjectMapper objectMapper;
    private final ProviderHeaderService providerHeaderService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea la ruta con configuracion y serializador.
     *
     * @param objectMapper serializador de payloads
     * @param providerHeaderService servicio de headers por proveedor
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public DynamicDirectOnlinePaymentRoute(ObjectMapper objectMapper,
            ProviderHeaderService providerHeaderService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.objectMapper = objectMapper;
        this.providerHeaderService = providerHeaderService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Configura la ruta dinamica para llamadas a proveedores.
     */
    @Override
    public void configure() {
        from("direct:direct-online-payment-requests")
                .routeId("dynamic-direct-online-payment-requests-route")
                .process(exchange -> {
                    Integer providerCode = exchange.getIn().getHeader("payment_provider_code", Integer.class);
                    String wsKey = "direct-online-payment-requests";

                    var cfg = gatewayWebServiceConfigService.getActiveConfig(providerCode, wsKey)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No hay configuracion activa en IN_PASARELA_WS para CODIGO_BILLETERA: "
                                            + providerCode + ", WS_KEY: " + wsKey));

                    String url = cfg.uri();
                    exchange.setProperty("url", url);
                    exchange.setProperty("httpMethod", cfg.method());
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

                    log.info("Request enviado a endpoint externo {}: {}", cfg.uri(), exchange.getIn().getBody());
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
