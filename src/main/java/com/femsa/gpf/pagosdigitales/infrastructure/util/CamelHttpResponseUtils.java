package com.femsa.gpf.pagosdigitales.infrastructure.util;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;

/**
 * Utilidades para preservar el codigo HTTP devuelto por llamadas Camel HTTP.
 */
public final class CamelHttpResponseUtils {

    private CamelHttpResponseUtils() {
    }

    /**
     * Ejecuta una llamada Camel y devuelve el body junto con el status HTTP del upstream.
     *
     * @param camel productor Camel
     * @param endpointUri endpoint a invocar
     * @param body cuerpo de la solicitud
     * @param headers headers enviados a la ruta
     * @return body y status HTTP devuelto por el upstream
     */
    public static CamelHttpResponse request(ProducerTemplate camel, String endpointUri, Object body,
            Map<String, Object> headers) {
        Exchange exchange = camel.request(endpointUri, requestExchange -> {
            requestExchange.getIn().setBody(body);
            if (headers != null) {
                headers.forEach(requestExchange.getIn()::setHeader);
            }
        });

        Message message = exchange.getMessage();
        Integer httpStatus = message == null ? null : message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        Object responseBody = message == null ? null : message.getBody();
        return new CamelHttpResponse(responseBody, httpStatus);
    }

    /**
     * Determina si un status HTTP corresponde a error.
     *
     * @param httpStatus status evaluado
     * @return true cuando el status es >= 400
     */
    public static boolean isErrorStatus(Integer httpStatus) {
        return httpStatus != null && httpStatus >= 400;
    }

    /**
     * Respuesta cruda de una llamada Camel con su status HTTP.
     *
     * @param body body devuelto
     * @param httpStatus status HTTP del upstream
     */
    public record CamelHttpResponse(Object body, Integer httpStatus) {
    }
}
