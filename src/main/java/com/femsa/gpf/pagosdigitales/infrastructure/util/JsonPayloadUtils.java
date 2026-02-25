package com.femsa.gpf.pagosdigitales.infrastructure.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utilidades para conversion y lectura de payloads JSON con paths anidados.
 */
public final class JsonPayloadUtils {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonPayloadUtils() {
    }

    /**
     * Convierte un payload crudo a mapa.
     *
     * @param raw payload crudo
     * @param mapper serializador JSON
     * @param errorMessage mensaje para excepcion de parseo
     * @return mapa con el contenido del payload
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object raw, ObjectMapper mapper, String errorMessage) {
        if (raw instanceof Map<?, ?> existingMap) {
            return (Map<String, Object>) existingMap;
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
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Obtiene un valor de un mapa usando notacion por puntos.
     *
     * @param map mapa base
     * @param path path anidado
     * @return valor encontrado o null
     */
    public static Object getValueByPath(Map<String, Object> map, String path) {
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

    /**
     * Escribe un valor en un mapa usando notacion por puntos.
     *
     * @param map mapa destino
     * @param path path anidado
     * @param value valor a escribir
     */
    @SuppressWarnings("unchecked")
    public static void setValueByPath(Map<String, Object> map, String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map<?, ?> nextMap) {
                current = (Map<String, Object>) nextMap;
                continue;
            }
            Map<String, Object> created = new LinkedHashMap<>();
            current.put(parts[i], created);
            current = created;
        }
        current.put(parts[parts.length - 1], value);
    }
}
