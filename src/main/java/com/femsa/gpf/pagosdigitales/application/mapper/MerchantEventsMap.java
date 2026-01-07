package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsMappingProperties.ResponseMapping;

/**
 * Mapper para requests y responses de merchant-events.
 */
@Component
public class MerchantEventsMap {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final MerchantEventsMappingProperties mappingProperties;

    public MerchantEventsMap(ObjectMapper mapper, MerchantEventsMappingProperties mappingProperties) {
        this.mapper = mapper;
        this.mappingProperties = mappingProperties;
    }

    /**
     * Construye el body hacia el proveedor segun el mapping configurado.
     *
     * @param req request generico
     * @param providerName proveedor seleccionado
     * @return body a enviar al proveedor
     */
    public Map<String, Object> mapProviderRequest(MerchantEventsRequest req, String providerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> reqMap = mapper.convertValue(req, MAP_TYPE);

        var mapping = mappingProperties.resolve(providerName).getRequest();
        if (mapping != null) {
            mapping.forEach((targetPath, sourcePath) -> {
                Object value = getValueByPath(reqMap, sourcePath);
                if (value != null) {
                    setValueByPath(body, targetPath, value);
                }
            });
        }

        return body;
    }

    /**
     * Mapea la respuesta del proveedor al response generico.
     *
     * @param req request generico
     * @param raw respuesta cruda del proveedor
     * @param providerName proveedor seleccionado
     * @return response generico
     */
    public MerchantEventsResponse mapProviderResponse(MerchantEventsRequest req, Object raw, String providerName) {
        Map<String, Object> map = toMap(raw);
        ResponseMapping responseMapping = mappingProperties.resolve(providerName).getResponse();

        MerchantEventsResponse resp = new MerchantEventsResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setPayment_provider_code(req.getPayment_provider_code());

        resp.setRequest_id(getValue(map, responseMapping.getRequestId(), String.class));
        resp.setResponse_datetime(getValue(map, responseMapping.getResponseDatetime(), String.class));

        return resp;
    }

    private <T> T getValue(Map<String, Object> map, String path, Class<T> type) {
        Object value = getValueByPath(map, path);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, type);
    }

    private Object getValueByPath(Map<String, Object> map, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        Object current = map;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> currentMap) {
                current = currentMap.get(part);
            } else {
                current = null;
                break;
            }
        }
        return current;
    }

    private void setValueByPath(Map<String, Object> map, String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private Map<String, Object> toMap(Object raw) {
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
            throw new RuntimeException("Error parseando respuesta de proveedor", e);
        }
    }
}
