package com.femsa.gpf.pagosdigitales.infrastructure.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInnerDetail;

/**
 * Utilidades para construir respuestas de error.
 */
public final class ApiErrorUtils {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ApiErrorUtils() {
    }

    /**
     * Extrae el objeto error desde una respuesta cruda del proveedor.
     *
     * @param rawResp respuesta cruda
     * @param mapper serializador JSON
     * @return error convertido o null si no existe
     */
    public static ErrorInfo extractProviderError(Object rawResp, ObjectMapper mapper) {
        return extractProviderError(rawResp, mapper, "error");
    }

    /**
     * Extrae el objeto error usando el path configurado.
     *
     * @param rawResp respuesta cruda
     * @param mapper serializador JSON
     * @param errorPath path del objeto error
     * @return error convertido o null si no existe
     */
    public static ErrorInfo extractProviderError(Object rawResp, ObjectMapper mapper, String errorPath) {
        Map<String, Object> map = toMap(rawResp, mapper);
        Object error = getValueByPath(map, errorPath);
        if (error == null) {
            return null;
        }
        return mapper.convertValue(error, ErrorInfo.class);
    }

    /**
     * Construye una respuesta de error con metadatos del request.
     *
     * @param chain cadena
     * @param store tienda
     * @param storeName nombre de tienda
     * @param pos punto de venta
     * @param paymentProviderCode codigo del proveedor
     * @param error error a incluir
     * @return respuesta de error
     */
    public static ApiErrorResponse buildResponse(Integer chain, Integer store, String storeName, Integer pos,
            Integer paymentProviderCode, ErrorInfo error) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setChain(chain);
        response.setStore(store);
        response.setStore_name(storeName);
        response.setPos(pos);
        response.setPayment_provider_code(paymentProviderCode);
        response.setError(error);
        return response;
    }

    /**
     * Construye un error de request invalido con detalles opcionales.
     *
     * @param message mensaje principal
     * @param field campo relacionado
     * @param fieldValue valor del campo
     * @param fieldMessage detalle del campo
     * @return estructura de error
     */
    public static ErrorInfo invalidRequest(String message, String field, String fieldValue, String fieldMessage) {
        ErrorInfo error = new ErrorInfo();
        error.setHttp_code(400);
        error.setCode("INVALID_REQUEST");
        error.setCategory("INVALID_REQUEST_ERROR");
        error.setMessage(message);
        error.setInformation_link(null);
        if (field != null) {
            ErrorInnerDetail detail = new ErrorInnerDetail();
            detail.setInner_code(null);
            detail.setField(field);
            detail.setField_value(fieldValue);
            detail.setField_message(fieldMessage);
            error.setInner_details(List.of(detail));
        } else {
            error.setInner_details(Collections.emptyList());
        }
        return error;
    }

    /**
     * Construye un error generico para fallos inesperados.
     *
     * @param httpCode codigo HTTP
     * @param message mensaje principal
     * @return estructura de error
     */
    public static ErrorInfo genericError(int httpCode, String message) {
        ErrorInfo error = new ErrorInfo();
        error.setHttp_code(httpCode);
        error.setCode("INTERNAL_ERROR");
        error.setCategory("INTERNAL_SERVER_ERROR");
        error.setMessage(message);
        error.setInformation_link(null);
        error.setInner_details(Collections.emptyList());
        return error;
    }

    private static Map<String, Object> toMap(Object raw, ObjectMapper mapper) {
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        try {
            if (raw instanceof byte[] bytes) {
                return mapper.readValue(bytes, MAP_TYPE);
            }
            if (raw instanceof String text) {
                return mapper.readValue(text, MAP_TYPE);
            }
            return mapper.convertValue(raw, MAP_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Object getValueByPath(Map<String, Object> map, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Object current = map;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> currentMap) {
                current = currentMap.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
