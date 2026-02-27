package com.femsa.gpf.pagosdigitales.application.mapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para solicitudes y respuestas de pago en linea.
 */
@Component
public class DirectOnlinePaymentMap {

    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};
    private static final String WS_KEY = "direct-online-payment-requests";

    private final ObjectMapper mapper;
    private final GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService;
    private final ServiceMappingConfigService serviceMappingConfigService;

    /**
     * Crea el mapper con dependencias de mapeo y configuracion.
     *
     * @param mapper serializador de JSON
     * @param gatewayWebServiceDefinitionService servicio de definiciones por BD
     * @param serviceMappingConfigService servicio de mapeo por BD
     */
    public DirectOnlinePaymentMap(ObjectMapper mapper,
            GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService,
            ServiceMappingConfigService serviceMappingConfigService) {
        this.mapper = mapper;
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
        this.serviceMappingConfigService = serviceMappingConfigService;
    }

    /**
     * Construye el request para el proveedor usando los mapeos configurados.
     *
     * @param req solicitud de pago entrante
     * @param providerName nombre del proveedor
     * @return cuerpo de la solicitud para el proveedor
     */
    public Map<String, Object> mapProviderRequest(DirectOnlinePaymentRequest req, String providerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> reqMap = mapper.convertValue(req, MAP_TYPE);

        var mapping = serviceMappingConfigService.getRequestBodyMappings(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);
        if (mapping != null) {
            mapping.forEach((targetPath, sourcePath) -> {
                Object value = getValueByPath(reqMap, sourcePath);
                if (value != null) {
                    JsonPayloadUtils.setValueByPath(body, targetPath, value);
                }
            });
        }

        var defaults = gatewayWebServiceDefinitionService.getDefaults(
                req.getPayment_provider_code(),
                WS_KEY,
                Map.of("now", LocalDateTime.now().format(REQUEST_DATETIME_FORMAT)));
        if (!defaults.isEmpty()) {
            defaults.forEach((targetPath, value) -> {
                if (value != null) {
                    JsonPayloadUtils.setValueByPath(body, targetPath, value);
                }
            });
        }

        body.put("request_datetime", LocalDateTime.now().format(REQUEST_DATETIME_FORMAT));

        return body;
    }

    /**
     * Normaliza la respuesta del proveedor al DTO interno.
     *
     * @param req solicitud original
     * @param raw respuesta cruda del proveedor
     * @param providerName nombre del proveedor
     * @return respuesta de pago en linea normalizada
     */
    public DirectOnlinePaymentResponse mapProviderResponse(DirectOnlinePaymentRequest req, Object raw, String providerName) {
        Map<String, Object> map = JsonPayloadUtils.toMap(raw, mapper, "Error parseando respuesta de proveedor");
        Map<String, String> responseMapping = serviceMappingConfigService.getResponseBodyMappings(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);

        DirectOnlinePaymentResponse resp = new DirectOnlinePaymentResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());
        resp.setPayment_provider_code(req.getPayment_provider_code());

        resp.setResponse_datetime(getValue(map, responseMapping.get("responseDatetime"), String.class));
        resp.setOperation_id(getValue(map, responseMapping.get("operationId"), String.class));
        resp.setBank_redirect_url(getValue(map, responseMapping.get("bankRedirectUrl"), String.class));
        resp.setPayment_expiration_datetime(getValue(map, responseMapping.get("paymentExpirationDatetime"), String.class));
        resp.setPayment_expiration_datetime_utc(getValue(map, responseMapping.get("paymentExpirationDatetimeUtc"), String.class));
        resp.setTransaction_id(getValue(map, responseMapping.get("transactionId"), String.class));

        List<Map<String, Object>> payableAmounts = getValue(map, responseMapping.get("payableAmounts"), LIST_MAP_TYPE);
        Map<String, String> payableAmountsItem = extractPrefixedMappings(responseMapping, "payableAmountsItem.");
        if (payableAmounts != null && !payableAmountsItem.isEmpty()) {
            payableAmounts = mapItemList(payableAmounts, payableAmountsItem);
        }
        resp.setPayable_amounts(payableAmounts);

        List<Map<String, Object>> paymentLocations = getValue(map, responseMapping.get("paymentLocations"), LIST_MAP_TYPE);
        Map<String, String> paymentLocationsItem = extractPrefixedMappings(responseMapping, "paymentLocationsItem.");
        if (paymentLocations != null && !paymentLocationsItem.isEmpty()) {
            paymentLocations = mapItemList(paymentLocations, paymentLocationsItem);
        }
        Map<String, String> paymentInstructionsItem = extractPrefixedMappings(responseMapping, "paymentInstructionsItem.");
        Map<String, String> howtoPayStepsItem = extractPrefixedMappings(responseMapping, "howtoPayStepsItem.");
        if (paymentLocations != null
                && (!paymentInstructionsItem.isEmpty() || !howtoPayStepsItem.isEmpty())) {
            paymentLocations = mapPaymentLocationsNested(paymentLocations, paymentInstructionsItem, howtoPayStepsItem);
        }
        resp.setPayment_locations(paymentLocations);

        return resp;
    }

    private <T> T getValue(Map<String, Object> map, String path, Class<T> type) {
        Object value = getValueByPath(map, path);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, type);
    }

    private <T> T getValue(Map<String, Object> map, String path, TypeReference<T> typeRef) {
        Object value = getValueByPath(map, path);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, typeRef);
    }

    private Object getValueByPath(Map<String, Object> map, String path) {
        return JsonPayloadUtils.getValueByPath(map, path);
    }

    private List<Map<String, Object>> mapItemList(List<Map<String, Object>> items, Map<String, String> mapping) {
        return items.stream().map(item -> {
            Map<String, Object> target = new LinkedHashMap<>();
            mapping.forEach((targetPath, sourcePath) -> {
                Object value = getValueByPath(item, sourcePath);
                if (value != null) {
                    JsonPayloadUtils.setValueByPath(target, targetPath, value);
                }
            });
            return target;
        }).toList();
    }

    private List<Map<String, Object>> mapPaymentLocationsNested(List<Map<String, Object>> items,
            Map<String, String> paymentInstructionsItem,
            Map<String, String> howtoPayStepsItem) {
        return items.stream().map(item -> {
            Map<String, Object> target = new LinkedHashMap<>(item);

            if (!paymentInstructionsItem.isEmpty()) {
                Object rawInstructions = getValueByPath(item, "payment_instructions");
                if (rawInstructions instanceof List) {
                    List<Map<String, Object>> mapped = mapItemList(mapper.convertValue(rawInstructions, LIST_MAP_TYPE),
                            paymentInstructionsItem);
                    target.put("payment_instructions", mapped);
                }
            }

            if (!howtoPayStepsItem.isEmpty()) {
                Object rawSteps = getValueByPath(item, "howto_pay_steps");
                if (rawSteps instanceof List) {
                    List<Map<String, Object>> mapped = mapItemList(mapper.convertValue(rawSteps, LIST_MAP_TYPE),
                            howtoPayStepsItem);
                    target.put("howto_pay_steps", mapped);
                }
            }

            return target;
        }).toList();
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

}
