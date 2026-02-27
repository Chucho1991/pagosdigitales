package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.infrastructure.config.GetBanksProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

/**
 * Ruta Camel dinamica para consultar bancos por proveedor.
 */
@Component
public class DynamicBankRoute extends RouteBuilder {

    private final GetBanksProperties props;
    private final ProviderHeaderService providerHeaderService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea la ruta con las propiedades de proveedores de bancos.
     *
     * @param props configuracion de proveedores
     * @param providerHeaderService servicio de headers por proveedor
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public DynamicBankRoute(GetBanksProperties props,
            ProviderHeaderService providerHeaderService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.props = props;
        this.providerHeaderService = providerHeaderService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Configura la ruta dinamica para la consulta de bancos.
     */
    @Override
    public void configure() {

        from("direct:getbanks")
                .routeId("dynamic-getbanks-route")
                .process(exchange -> {

                    String proveedor = exchange.getIn().getHeader("getbanks", String.class);
                    Integer providerCode = exchange.getIn().getHeader("payment_provider_code", Integer.class);
                    String wsKey = "getbanks";

                    var wsCfg = gatewayWebServiceConfigService.getActiveConfig(providerCode, wsKey)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No hay configuracion activa en IN_PASARELA_WS para CODIGO_BILLETERA: "
                                            + providerCode + ", WS_KEY: " + wsKey));

                    String country = exchange.getIn().getHeader("country_code", String.class);
                    String now = exchange.getIn().getHeader("now", String.class);

                    StringBuilder url = new StringBuilder(wsCfg.uri());
                    var providerCfg = props.getProviders() == null ? null : props.getProviders().get(proveedor);

                    if (providerCfg != null && providerCfg.getQuery() != null && !providerCfg.getQuery().isEmpty()) {
                        url.append(url.indexOf("?") >= 0 ? "&" : "?");

                        providerCfg.getQuery().forEach((k, v) -> {
                            String value = v;

                            if (country != null) {
                                value = value.replace("${country_code}", country);
                            }

                            if (now != null) {
                                value = value.replace("${now}", now);
                            }

                            url.append(k)
                                    .append("=")
                                    .append(value)
                                    .append("&");
                        });

                        url.deleteCharAt(url.length() - 1);
                    } else {
                        url.append(url.indexOf("?") >= 0 ? "&" : "?")
                                .append("country_code=").append(country == null ? "" : country)
                                .append("&channel=1")
                                .append("&request_datetime=").append(now == null ? "" : now)
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
