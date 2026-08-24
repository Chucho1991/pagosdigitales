package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ExternalServiceHttpProperties;
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
    private final ExternalServiceHttpProperties externalServiceHttpProperties;
    private final ObjectMapper objectMapper;

    /**
     * Crea la ruta con las propiedades de proveedores de pagos.
     *
     * @param gatewayWebServiceDefinitionService servicio de definiciones por BD
     * @param providerHeaderService servicio de headers por proveedor
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     * @param externalServiceHttpProperties propiedades de timeout HTTP externo
     * @param objectMapper serializador de cuerpos JSON
     */
    public DynamicPaymentsRoute(GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService,
            ProviderHeaderService providerHeaderService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService,
            ExternalServiceHttpProperties externalServiceHttpProperties,
            ObjectMapper objectMapper) {
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
        this.providerHeaderService = providerHeaderService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
        this.externalServiceHttpProperties = externalServiceHttpProperties;
        this.objectMapper = objectMapper;
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
                    if ("JSON".equalsIgnoreCase(wsCfg.requestType())) {
                        Object body = exchange.getIn().getBody();
                        if (body == null) {
                            throw new IllegalArgumentException(
                                    "No se construyo el body JSON para CODIGO_BILLETERA: " + providerCode);
                        }
                        if (!(body instanceof String)) {
                            exchange.getIn().setBody(objectMapper.writeValueAsString(body));
                        }
                    } else {
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
                        queryParams.forEach((key, value) -> url
                                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                                .append("=")
                                .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                                .append("&"));
                        url.deleteCharAt(url.length() - 1);
                    }

                    String resolvedUrl = url.toString();
                    exchange.setProperty("providerCode", providerCode);
                    exchange.setProperty("url", resolvedUrl);
                    exchange.setProperty("httpMethod", wsCfg.method());
                    exchange.setProperty("endpointSuffix", externalServiceHttpProperties.buildEndpointSuffix(resolvedUrl));

                    var providerHeaders = providerHeaderService.getHeadersByProviderCode(providerCode);
                    if (providerHeaders.isEmpty()) {
                        throw new IllegalArgumentException(
                                "No hay headers configurados para CODIGO_BILLETERA: " + providerCode);
                    }
                    providerHeaders.forEach(exchange.getIn()::setHeader);

                    log.info("Request payments a {} con metodo {}, tipo {}, Content-Type {}",
                            resolvedUrl, wsCfg.method(), wsCfg.requestType(),
                            exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
                })
                .setHeader("CamelHttpMethod", exchangeProperty("httpMethod"))
                .toD("${exchangeProperty.url}${exchangeProperty.endpointSuffix}")
                .convertBodyTo(String.class)
                .process(exchange -> {
                    Integer providerCode = exchange.getProperty("providerCode", Integer.class);
                    Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                    String responseBody = exchange.getMessage().getBody(String.class);
                    log.info("Respuesta payments de proveedor {}: HTTP {}, longitud={}",
                            providerCode, httpCode, responseBody == null ? 0 : responseBody.length());
                    if (responseBody == null || responseBody.isBlank()) {
                        throw new IllegalStateException("El proveedor " + providerCode
                                + " devolvio HTTP " + (httpCode == null ? "desconocido" : httpCode)
                                + " sin contenido");
                    }
                });
    }
}
