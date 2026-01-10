package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.infrastructure.config.GetBanksProperties;

/**
 * Ruta Camel dinamica para consultar bancos por proveedor.
 */
@Component
public class DynamicBankRoute extends RouteBuilder {

    private final GetBanksProperties props;

    /**
     * Crea la ruta con las propiedades de proveedores de bancos.
     *
     * @param props configuracion de proveedores
     */
    public DynamicBankRoute(GetBanksProperties props) {
        this.props = props;
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

                    var cfg = props.getProviders().get(proveedor);

                    if (cfg == null || !cfg.isEnabled()) {
                        throw new IllegalArgumentException("Proveedor no habilitado: " + proveedor);
                    }

                    // Valores opcionales desde controller
                    String country = (String) exchange.getIn().getHeader("country_code");
                    String now = (String) exchange.getIn().getHeader("now");

                    // Construcción de la URL
                    StringBuilder url = new StringBuilder(cfg.getUrl());

                    if (cfg.getQuery() != null && !cfg.getQuery().isEmpty()) {
                        url.append("?");

                        cfg.getQuery().forEach((k, v) -> {

                            // Reemplazo seguro SIN usar tipos de Camel
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

                        // Quitar último &
                        url.deleteCharAt(url.length() - 1);
                    }

                    // Guardar URL lista para toD()
                    exchange.setProperty("url", url.toString());

                    // Método HTTP
                    exchange.setProperty("httpMethod", cfg.getMethod());
                    exchange.setProperty("endpointSuffix", url.indexOf("?") >= 0
                            ? "&throwExceptionOnFailure=false"
                            : "?throwExceptionOnFailure=false");

                    // INCLUIR HEADERS X-API-KEY Y X-VERSION DESDE YAML
                    if (cfg.getHeaders() != null) {
                        cfg.getHeaders().forEach((headerName, headerValue) -> {
                            exchange.getIn().setHeader(headerName, headerValue);
                        });
                    }

                    // Logging opcional para verificar
                    log.info("URL construida: {}", url);
                    log.info("Headers enviados: {}", cfg.getHeaders());
                })
                // Llamada dinámica REST
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}");
    }
}
