package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta Camel dinamica para la consulta de pagos por proveedor.
 */
@Component
public class DynamicPaymentsRoute extends RouteBuilder {

    private final GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService;
    private final ProviderHeaderService providerHeaderService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea la ruta con las propiedades de proveedores de pagos.
     *
     * @param gatewayWebServiceDefinitionService servicio de definiciones por BD
     * @param providerHeaderService servicio de headers por proveedor
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public DynamicPaymentsRoute(GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService,
            ProviderHeaderService providerHeaderService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
        this.providerHeaderService = providerHeaderService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Configura la ruta dinamica para la consulta de pagos.
     */
    @Override
    public void configure() {
        from("direct:payments")
                .routeId("dynamic-payments-route")
                .process(exchange -> {
                    Integer providerCode = exchange.getIn().getHeader("payment_provider_code", Integer.class);
                    String wsKey = "payments";

                    var wsCfg = gatewayWebServiceConfigService.getActiveConfig(providerCode, wsKey)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No hay configuracion activa en IN_PASARELA_WS para CODIGO_BILLETERA: "
                                            + providerCode + ", WS_KEY: " + wsKey));

                    String operationId = exchange.getIn().getHeader("operation_id", String.class);
                    String requestDatetime = exchange.getIn().getHeader("request_datetime", String.class);

                    StringBuilder url = new StringBuilder(wsCfg.uri());
                    var queryParams = gatewayWebServiceDefinitionService.getQueryParams(
                            providerCode,
                            wsKey,
                            java.util.Map.of(
                                    "operation_id", operationId == null ? "" : operationId,
                                    "request_datetime", requestDatetime == null ? "" : requestDatetime,
                                    "now", requestDatetime == null ? "" : requestDatetime));
                    if (queryParams.isEmpty()) {
                        throw new IllegalArgumentException(
                                "No hay parametros QUERY configurados en IN_PASARELA_WS_DEFS para CODIGO_BILLETERA: "
                                        + providerCode + ", WS_KEY: " + wsKey);
                    }
                    url.append(url.indexOf("?") >= 0 ? "&" : "?");
                    queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
                    url.deleteCharAt(url.length() - 1);

                    exchange.setProperty("url", url.toString());
                    exchange.setProperty("httpMethod", wsCfg.method());
                    exchange.setProperty("endpointSuffix", url.indexOf("?") >= 0
                            ? "&throwExceptionOnFailure=false"
                            : "?throwExceptionOnFailure=false");

                    var providerHeaders = providerHeaderService.getHeadersByProviderCode(providerCode);
                    if (providerHeaders.isEmpty()) {
                        throw new IllegalArgumentException(
                                "No hay headers configurados para CODIGO_BILLETERA: " + providerCode);
                    }
                    providerHeaders.forEach(exchange.getIn()::setHeader);

                    log.info("URL construida: {}", url);
                    log.info("Headers enviados: {}", providerHeaders);
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
