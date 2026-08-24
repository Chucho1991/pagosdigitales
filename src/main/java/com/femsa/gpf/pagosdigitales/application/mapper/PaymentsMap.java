package com.femsa.gpf.pagosdigitales.application.mapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperationActivity;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para respuestas del endpoint de pagos.
 */
@Component
public class PaymentsMap {

    private static final TypeReference<java.util.List<PaymentOperation>> OPERATIONS_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String WS_KEY = "payments";

    private final ObjectMapper mapper;
    private final ServiceMappingConfigService serviceMappingConfigService;
    private final GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService;

    /**
     * Crea el mapper con el serializador y las propiedades.
     *
     * @param mapper serializador de JSON
     * @param serviceMappingConfigService servicio de mapeo por BD
     * @param gatewayWebServiceDefinitionService servicio de defaults por BD
     */
    public PaymentsMap(ObjectMapper mapper,
            ServiceMappingConfigService serviceMappingConfigService,
            GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService) {
        this.mapper = mapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
    }

    /**
     * Construye el body de consulta para proveedores configurados como JSON.
     *
     * @param req solicitud generica de consulta
     * @param providerName proveedor seleccionado
     * @return body mapeado usando AD_MAPEO_SERVICIOS e IN_PASARELA_WS_DEFS
     */
    public Map<String, Object> mapProviderRequest(PaymentsRequest req, String providerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> reqMap = mapper.convertValue(req, MAP_TYPE);
        Map<String, String> mappings = serviceMappingConfigService.getRequestBodyMappings(
                req.getPayment_provider_code(), WS_KEY, providerName);
        Map<String, String> dataTypes = serviceMappingConfigService.getRequestBodyDataTypes(
                req.getPayment_provider_code(), WS_KEY, providerName);

        mappings.forEach((targetPath, sourcePath) -> {
            Object value = JsonPayloadUtils.getValueByPath(reqMap, sourcePath);
            if (value != null) {
                JsonPayloadUtils.setValueByPath(body, targetPath,
                        convertConfiguredType(value, dataTypes.get(targetPath)));
            }
        });

        Map<String, Object> defaults = gatewayWebServiceDefinitionService.getDefaults(
                req.getPayment_provider_code(), WS_KEY,
                Map.of(
                        "operation_id", req.getOperation_id(),
                        "request_datetime", req.getRequest_datetime() == null ? "" : req.getRequest_datetime()));
        defaults.forEach((path, value) -> {
            if (value != null) {
                JsonPayloadUtils.setValueByPath(body, path, value);
            }
        });
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
        List<PaymentOperation> operations = getValue(
                map, responseMapping.get("paymentOperations"), OPERATIONS_TYPE);
        if (operations == null) {
            operations = mapSinglePaymentOperation(map, responseMapping);
        }
        resp.setPayment_operations(operations);

        return resp;
    }

    private List<PaymentOperation> mapSinglePaymentOperation(
            Map<String, Object> source, Map<String, String> responseMapping) {
        Map<String, String> operationMapping = extractPrefixedMappings(responseMapping, "paymentOperation.");
        if (operationMapping.isEmpty()) {
            return null;
        }

        Map<String, Object> target = new LinkedHashMap<>();
        operationMapping.forEach((targetPath, sourcePath) -> {
            Object value = JsonPayloadUtils.getValueByPath(source, sourcePath);
            if (value != null) {
                JsonPayloadUtils.setValueByPath(target, targetPath, value);
            }
        });

        PaymentOperation operation = mapper.convertValue(target, PaymentOperation.class);
        operation.setRefunds_related(List.of());

        Map<String, String> activityMapping = extractPrefixedMappings(
                responseMapping, "paymentOperationActivity.");
        if (!activityMapping.isEmpty()) {
            Map<String, Object> activityTarget = new LinkedHashMap<>();
            activityMapping.forEach((targetPath, sourcePath) -> {
                Object value = JsonPayloadUtils.getValueByPath(source, sourcePath);
                if (value != null) {
                    JsonPayloadUtils.setValueByPath(activityTarget, targetPath, value);
                }
            });
            operation.setOperation_activities(List.of(
                    mapper.convertValue(activityTarget, PaymentOperationActivity.class)));
        }

        return List.of(operation);
    }

    private Map<String, String> extractPrefixedMappings(Map<String, String> mappings, String prefix) {
        Map<String, String> extracted = new LinkedHashMap<>();
        mappings.forEach((appPath, externalPath) -> {
            if (appPath.startsWith(prefix)) {
                extracted.put(appPath.substring(prefix.length()), externalPath);
            }
        });
        return extracted;
    }

    private Object convertConfiguredType(Object value, String dataType) {
        if (value == null || dataType == null || dataType.isBlank()) {
            return value;
        }
        return switch (dataType.toUpperCase()) {
            case "STRING", "DATETIME", "DATE" -> value.toString();
            case "NUMBER" -> new BigDecimal(value.toString());
            case "BOOLEAN" -> mapper.convertValue(value, Boolean.class);
            case "ARRAY" -> mapper.convertValue(value, List.class);
            case "OBJECT" -> mapper.convertValue(value, Map.class);
            default -> value;
        };
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
