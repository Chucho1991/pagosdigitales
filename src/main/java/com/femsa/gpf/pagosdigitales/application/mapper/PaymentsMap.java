package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para respuestas del endpoint de pagos.
 */
@Component
public class PaymentsMap {

    private static final TypeReference<java.util.List<PaymentOperation>> OPERATIONS_TYPE =
            new TypeReference<>() {};
    private static final String WS_KEY = "payments";

    private final ObjectMapper mapper;
    private final ServiceMappingConfigService serviceMappingConfigService;

    /**
     * Crea el mapper con el serializador y las propiedades.
     *
     * @param mapper serializador de JSON
     * @param serviceMappingConfigService servicio de mapeo por BD
     */
    public PaymentsMap(ObjectMapper mapper, ServiceMappingConfigService serviceMappingConfigService) {
        this.mapper = mapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
    }

    /**
     * Mapea la respuesta del proveedor al response generico.
     *
     * @param req request generico
     * @param raw respuesta cruda del proveedor
     * @param providerName proveedor seleccionado
     * @return response generico
     */
    public PaymentsResponse mapProviderResponse(PaymentsRequest req, Object raw, String providerName) {
        Map<String, Object> map = JsonPayloadUtils.toMap(raw, mapper, "Error parseando respuesta de proveedor");
        Map<String, String> responseMapping = serviceMappingConfigService.getResponseBodyMappings(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);

        PaymentsResponse resp = new PaymentsResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());
        resp.setPayment_provider_code(req.getPayment_provider_code());

        resp.setRequest_id(getValue(map, responseMapping.get("requestId"), String.class));
        resp.setResponse_datetime(getValue(map, responseMapping.get("responseDatetime"), String.class));
        resp.setPayment_operations(getValue(map, responseMapping.get("paymentOperations"), OPERATIONS_TYPE));

        return resp;
    }

    private <T> T getValue(Map<String, Object> map, String path, Class<T> type) {
        Object value = JsonPayloadUtils.getValueByPath(map, path);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, type);
    }

    private <T> T getValue(Map<String, Object> map, String path, TypeReference<T> typeRef) {
        Object value = JsonPayloadUtils.getValueByPath(map, path);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, typeRef);
    }
}
