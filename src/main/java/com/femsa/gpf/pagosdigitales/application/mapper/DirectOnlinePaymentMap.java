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
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentMappingProperties.ResponseMapping;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para solicitudes y respuestas de pago en linea.
 */
@Component
public class DirectOnlinePaymentMap {

    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String WS_KEY = "direct-online-payment-requests";

    private final ObjectMapper mapper;
    private final GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService;
    private final DirectOnlinePaymentMappingProperties mappingProperties;

    /**
     * Crea el mapper con dependencias de mapeo y configuracion.
     *
     * @param mapper serializador de JSON
     * @param gatewayWebServiceDefinitionService servicio de definiciones por BD
     * @param mappingProperties configuracion de mapeo
     */
    public DirectOnlinePaymentMap(ObjectMapper mapper,
            GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService,
            DirectOnlinePaymentMappingProperties mappingProperties) {
        this.mapper = mapper;
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
        this.mappingProperties = mappingProperties;
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

        var mapping = mappingProperties.resolve(providerName).getRequest();
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
        ResponseMapping responseMapping = mappingProperties.resolve(providerName).getResponse();

        DirectOnlinePaymentResponse resp = new DirectOnlinePaymentResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());
        resp.setPayment_provider_code(req.getPayment_provider_code());

        resp.setResponse_datetime(getValue(map, responseMapping.getResponseDatetime(), String.class));
        resp.setOperation_id(getValue(map, responseMapping.getOperationId(), String.class));
        resp.setBank_redirect_url(getValue(map, responseMapping.getBankRedirectUrl(), String.class));
        resp.setPayment_expiration_datetime(getValue(map, responseMapping.getPaymentExpirationDatetime(), String.class));
        resp.setPayment_expiration_datetime_utc(getValue(map, responseMapping.getPaymentExpirationDatetimeUtc(), String.class));
        resp.setTransaction_id(getValue(map, responseMapping.getTransactionId(), String.class));
        List<Map<String, Object>> payableAmounts = getValue(map, responseMapping.getPayableAmounts(),
                new TypeReference<List<Map<String, Object>>>() {});
        if (payableAmounts != null && !responseMapping.getPayableAmountsItem().isEmpty()) {
            payableAmounts = mapItemList(payableAmounts, responseMapping.getPayableAmountsItem());
        }
        resp.setPayable_amounts(payableAmounts);

        List<Map<String, Object>> paymentLocations = getValue(map, responseMapping.getPaymentLocations(),
                new TypeReference<List<Map<String, Object>>>() {});
        if (paymentLocations != null && !responseMapping.getPaymentLocationsItem().isEmpty()) {
            paymentLocations = mapItemList(paymentLocations, responseMapping.getPaymentLocationsItem());
        }
        if (paymentLocations != null
                && (!responseMapping.getPaymentInstructionsItem().isEmpty()
                || !responseMapping.getHowtoPayStepsItem().isEmpty())) {
            paymentLocations = mapPaymentLocationsNested(paymentLocations, responseMapping);
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
            ResponseMapping responseMapping) {
        return items.stream().map(item -> {
            Map<String, Object> target = new LinkedHashMap<>(item);

            if (!responseMapping.getPaymentInstructionsItem().isEmpty()) {
                Object rawInstructions = getValueByPath(item, "payment_instructions");
                if (rawInstructions instanceof List) {
                    List<Map<String, Object>> mapped = mapItemList(
                            mapper.convertValue(rawInstructions, new TypeReference<List<Map<String, Object>>>() {}),
                            responseMapping.getPaymentInstructionsItem());
                    target.put("payment_instructions", mapped);
                }
            }

            if (!responseMapping.getHowtoPayStepsItem().isEmpty()) {
                Object rawSteps = getValueByPath(item, "howto_pay_steps");
                if (rawSteps instanceof List) {
                    List<Map<String, Object>> mapped = mapItemList(
                            mapper.convertValue(rawSteps, new TypeReference<List<Map<String, Object>>>() {}),
                            responseMapping.getHowtoPayStepsItem());
                    target.put("howto_pay_steps", mapped);
                }
            }

            return target;
        }).toList();
    }

}
