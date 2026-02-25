package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.config.PaymentsMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.PaymentsMappingProperties.ResponseMapping;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para respuestas del endpoint de pagos.
 */
@Component
public class PaymentsMap {

    private static final TypeReference<java.util.List<PaymentOperation>> OPERATIONS_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final PaymentsMappingProperties mappingProperties;

    /**
     * Crea el mapper con el serializador y las propiedades.
     *
     * @param mapper serializador de JSON
     * @param mappingProperties configuracion de mapeo
     */
    public PaymentsMap(ObjectMapper mapper, PaymentsMappingProperties mappingProperties) {
        this.mapper = mapper;
        this.mappingProperties = mappingProperties;
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
        ResponseMapping responseMapping = mappingProperties.resolve(providerName).getResponse();

        PaymentsResponse resp = new PaymentsResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());
        resp.setPayment_provider_code(req.getPayment_provider_code());

        resp.setRequest_id(getValue(map, responseMapping.getRequestId(), String.class));
        resp.setResponse_datetime(getValue(map, responseMapping.getResponseDatetime(), String.class));
        resp.setPayment_operations(getValue(map, responseMapping.getPaymentOperations(), OPERATIONS_TYPE));

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
