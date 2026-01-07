package com.femsa.gpf.pagosdigitales.infrastructure.util;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utilidades comunes para la aplicacion.
 */
public final class AppUtils {

    private AppUtils() {
    }

    /**
     * Convierte un payload a texto legible para logging.
     *
     * @param payload objeto a serializar
     * @param objectMapper serializador JSON
     * @return representacion en texto del payload
     */
    public static String formatPayload(Object payload, ObjectMapper objectMapper) {
        if (payload == null) {
            return "null";
        }
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }
}
