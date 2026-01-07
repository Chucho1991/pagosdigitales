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
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentProperties;

@Component
public class DirectOnlinePaymentMap {

    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final DirectOnlinePaymentProperties properties;
    private final DirectOnlinePaymentMappingProperties mappingProperties;

    public DirectOnlinePaymentMap(ObjectMapper mapper,
            DirectOnlinePaymentProperties properties,
            DirectOnlinePaymentMappingProperties mappingProperties) {
        this.mapper = mapper;
        this.properties = properties;
        this.mappingProperties = mappingProperties;
    }

    public Map<String, Object> mapProviderRequest(DirectOnlinePaymentRequest req, String providerName) {
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

        DirectOnlinePaymentProperties.Defaults defaults = null;
        if (properties.getProviders() != null && properties.getProviders().get(providerName) != null) {
            defaults = properties.getProviders().get(providerName).getDefaults();
        }
        if (defaults != null) {
            String applicationId = defaults.getApplication_id();
            if (applicationId != null && !applicationId.isBlank()) {
                body.put("application_id", applicationId);
            }
            String paymentOkUrl = defaults.getPayment_ok_url();
            if (paymentOkUrl != null && !paymentOkUrl.isBlank()) {
                body.put("payment_ok_url", paymentOkUrl);
            }
            String paymentErrorUrl = defaults.getPayment_error_url();
            if (paymentErrorUrl != null && !paymentErrorUrl.isBlank()) {
                body.put("payment_error_url", paymentErrorUrl);
            }
        }

        body.put("request_datetime", LocalDateTime.now().format(REQUEST_DATETIME_FORMAT));

        return body;
    }

    public DirectOnlinePaymentResponse mapProviderResponse(DirectOnlinePaymentRequest req, Object raw, String providerName) {
        Map<String, Object> map = toMap(raw);
        ResponseMapping responseMapping = mappingProperties.resolve(providerName).getResponse();

        DirectOnlinePaymentResponse resp = new DirectOnlinePaymentResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
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

    private List<Map<String, Object>> mapItemList(List<Map<String, Object>> items, Map<String, String> mapping) {
        return items.stream().map(item -> {
            Map<String, Object> target = new LinkedHashMap<>();
            mapping.forEach((targetPath, sourcePath) -> {
                Object value = getValueByPath(item, sourcePath);
                if (value != null) {
                    setValueByPath(target, targetPath, value);
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
