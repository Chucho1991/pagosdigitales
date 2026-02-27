package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.infrastructure.config.PaymentsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta Camel dinamica para la consulta de pagos por proveedor.
 */
@Component
public class DynamicPaymentsRoute extends RouteBuilder {

    private final PaymentsProperties props;
    private final ProviderHeaderService providerHeaderService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea la ruta con las propiedades de proveedores de pagos.
     *
     * @param props configuracion de proveedores
     * @param providerHeaderService servicio de headers por proveedor
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public DynamicPaymentsRoute(PaymentsProperties props,
            ProviderHeaderService providerHeaderService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.props = props;
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
                    String proveedor = exchange.getIn().getHeader("payments", String.class);
                    Integer providerCode = exchange.getIn().getHeader("payment_provider_code", Integer.class);
                    String wsKey = "payments";

                    var wsCfg = gatewayWebServiceConfigService.getActiveConfig(providerCode, wsKey)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No hay configuracion activa en IN_PASARELA_WS para CODIGO_BILLETERA: "
                                            + providerCode + ", WS_KEY: " + wsKey));

                    String operationId = exchange.getIn().getHeader("operation_id", String.class);
                    String requestDatetime = exchange.getIn().getHeader("request_datetime", String.class);

                    StringBuilder url = new StringBuilder(wsCfg.uri());
                    var providerCfg = props.getProviders() == null ? null : props.getProviders().get(proveedor);

                    if (providerCfg != null && providerCfg.getQuery() != null && !providerCfg.getQuery().isEmpty()) {
                        url.append(url.indexOf("?") >= 0 ? "&" : "?");

                        providerCfg.getQuery().forEach((k, v) -> {
                            String value = v;

                            if (operationId != null) {
                                value = value.replace("${operation_id}", operationId);
                            }

                            if (requestDatetime != null) {
                                value = value.replace("${request_datetime}", requestDatetime);
                            }

                            url.append(k)
                                    .append("=")
                                    .append(value)
                                    .append("&");
                        });

                        url.deleteCharAt(url.length() - 1);
                    } else {
                        url.append(url.indexOf("?") >= 0 ? "&" : "?")
                                .append("operation_id=").append(operationId == null ? "" : operationId)
                                .append("&request_datetime=").append(requestDatetime == null ? "" : requestDatetime)
                                .append("&limit=100");
                    }

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
