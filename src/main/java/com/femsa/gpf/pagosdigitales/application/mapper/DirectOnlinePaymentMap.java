package com.femsa.gpf.pagosdigitales.application.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentResponse;
import com.femsa.gpf.pagosdigitales.application.ports.out.ProviderTransactionSequencePort;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PointOfSaleConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para solicitudes y respuestas de pago en linea.
 */
@Component
public class DirectOnlinePaymentMap {

    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ZoneId LOCAL_TIME_ZONE = ZoneId.of("America/Guayaquil");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {
    };
    private static final String WS_KEY = "direct-online-payment-requests";
    private static final String EXPIRED_TIME_PATH = "expiredTime";
    private static final String HOWTO_PAY_STEP_INSTRUCTION_PATH = "howtoPayStepInstruction";
    private static final String BANK_DEUNA = "deuna";
    private static final String BANK_JEPFASTER = "jepfaster";

    private final ObjectMapper mapper;
    private final GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService;
    private final ServiceMappingConfigService serviceMappingConfigService;
    private final PointOfSaleConfigService pointOfSaleConfigService;
    private final ProviderTransactionSequencePort providerTransactionSequencePort;

    /**
     * Crea el mapper con dependencias de mapeo y configuracion.
     *
     * @param mapper                             serializador de JSON
     * @param gatewayWebServiceDefinitionService servicio de definiciones por BD
     * @param serviceMappingConfigService        servicio de mapeo por BD
     * @param pointOfSaleConfigService            servicio de puntos de venta externos
     * @param providerTransactionSequencePort     generador de secuenciales por proveedor
     */
    public DirectOnlinePaymentMap(ObjectMapper mapper,
            GatewayWebServiceDefinitionService gatewayWebServiceDefinitionService,
            ServiceMappingConfigService serviceMappingConfigService,
            PointOfSaleConfigService pointOfSaleConfigService,
            ProviderTransactionSequencePort providerTransactionSequencePort) {
        this.mapper = mapper;
        this.gatewayWebServiceDefinitionService = gatewayWebServiceDefinitionService;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.pointOfSaleConfigService = pointOfSaleConfigService;
        this.providerTransactionSequencePort = providerTransactionSequencePort;
    }

    /**
     * Construye el request para el proveedor usando los mapeos configurados.
     *
     * @param req          solicitud de pago entrante
     * @param providerName nombre del proveedor
     * @return cuerpo de la solicitud para el proveedor
     */
    public Map<String, Object> mapProviderRequest(DirectOnlinePaymentRequest req, String providerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> reqMap = mapper.convertValue(req, MAP_TYPE);
        applyJepStoreFallbacks(reqMap, req.getStore(), providerName);

        var mapping = serviceMappingConfigService.getRequestBodyMappings(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);
        var dataTypes = serviceMappingConfigService.getRequestBodyDataTypes(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);
        if (mapping != null) {
            mapping.forEach((targetPath, sourcePath) -> {
                Object value = getValueByPath(reqMap, sourcePath);
                if (value != null) {
                    Object typedValue = convertConfiguredType(value, dataTypes.get(targetPath));
                    JsonPayloadUtils.setValueByPath(body, targetPath, typedValue);
                }
            });
        }

        Map<String, Object> defaultRuntimeValues = buildDefaultRuntimeValues(reqMap);
        var defaults = gatewayWebServiceDefinitionService.getDefaults(
                req.getPayment_provider_code(),
                WS_KEY,
                defaultRuntimeValues);
        if (!defaults.isEmpty()) {
            defaults.forEach((targetPath, value) -> {
                if (value != null && !HOWTO_PAY_STEP_INSTRUCTION_PATH.equals(targetPath)) {
                    Object defaultValue = EXPIRED_TIME_PATH.equals(targetPath)
                            ? new BigDecimal(value.toString())
                            : value;
                    JsonPayloadUtils.setValueByPath(body, targetPath, defaultValue);
                }
            });
        }

        if (BANK_DEUNA.equalsIgnoreCase(providerName)) {
            String pointOfSale = pointOfSaleConfigService.findPointOfSale(
                    req.getPayment_provider_code(), req.getChain(), req.getStore(), req.getPos())
                    .orElseThrow(() -> new IllegalArgumentException(buildMissingPointOfSaleMessage(req)));
            body.put("pointOfSale", pointOfSale);
        }

        overrideMerchantSalesIdWithProviderSequence(body, providerName);

        if (!"jepfaster".equalsIgnoreCase(providerName) && !"deuna".equalsIgnoreCase(providerName)) {
            body.put("request_datetime", LocalDateTime.now().format(REQUEST_DATETIME_FORMAT));
        }

        return body;
    }

    private void applyJepStoreFallbacks(Map<String, Object> reqMap, Integer store, String providerName) {
        if (!BANK_JEPFASTER.equalsIgnoreCase(providerName) || store == null) {
            return;
        }

        String storeCode = store.toString();
        putStringFallback(reqMap, "store_name", storeCode);
        putStringFallback(reqMap, "city", storeCode);
        putStringFallback(reqMap, "store_address", storeCode);
    }

    private void putStringFallback(Map<String, Object> values, String key, String fallback) {
        Object currentValue = values.get(key);
        if (currentValue == null || currentValue.toString().isBlank()) {
            values.put(key, fallback);
        }
    }

    private void overrideMerchantSalesIdWithProviderSequence(Map<String, Object> body, String providerName) {
        if (BANK_JEPFASTER.equalsIgnoreCase(providerName)) {
            BigDecimal sequence = providerTransactionSequencePort.nextJepTransactionId();
            body.put("codigoTransaccion", sequence.toPlainString());
        } else if (BANK_DEUNA.equalsIgnoreCase(providerName)) {
            BigDecimal sequence = providerTransactionSequencePort.nextDeunaTransactionId();
            body.put("internalTransactionReference", sequence.toPlainString());
        }
    }

    private Map<String, Object> buildDefaultRuntimeValues(Map<String, Object> reqMap) {
        Map<String, Object> runtimeValues = new LinkedHashMap<>();
        reqMap.forEach((key, value) -> {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                runtimeValues.put(key, value);
            }
        });
        runtimeValues.put("now", LocalDateTime.now().format(REQUEST_DATETIME_FORMAT));
        return runtimeValues;
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

    /**
     * Normaliza la respuesta del proveedor al DTO interno.
     *
     * @param req          solicitud original
     * @param raw          respuesta cruda del proveedor
     * @param providerName nombre del proveedor
     * @return respuesta de pago en linea normalizada
     */
    public DirectOnlinePaymentResponse mapProviderResponse(DirectOnlinePaymentRequest req, Object raw,
            String providerName) {
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
        String expirationDatetime = getValue(
                map, responseMapping.get("paymentExpirationDatetime"), String.class);
        String expirationDatetimeUtc = getValue(
                map, responseMapping.get("paymentExpirationDatetimeUtc"), String.class);
        Map<String, Object> configuredDefaults = gatewayWebServiceDefinitionService.getDefaults(
                req.getPayment_provider_code(), WS_KEY, Map.of());
        if (expirationDatetime == null || expirationDatetimeUtc == null) {
            Object expiredTime = getValueByPath(configuredDefaults, EXPIRED_TIME_PATH);
            if (expiredTime != null) {
                long expirationMinutes = new BigDecimal(expiredTime.toString()).longValueExact();
                Instant expirationInstant = Instant.now().plusSeconds(expirationMinutes * 60L);
                if (expirationDatetime == null) {
                    expirationDatetime = LocalDateTime.ofInstant(expirationInstant, LOCAL_TIME_ZONE)
                            .format(REQUEST_DATETIME_FORMAT);
                }
                if (expirationDatetimeUtc == null) {
                    expirationDatetimeUtc = LocalDateTime.ofInstant(expirationInstant, ZoneOffset.UTC)
                            .format(REQUEST_DATETIME_FORMAT);
                }
            }
        }
        resp.setTransaction_id(getValue(map, responseMapping.get("transactionId"), String.class));

        List<Map<String, Object>> payableAmounts = getValue(map, responseMapping.get("payableAmounts"), LIST_MAP_TYPE);
        Map<String, String> payableAmountsItem = extractPrefixedMappings(responseMapping, "payableAmountsItem.");
        if (payableAmounts != null && !payableAmountsItem.isEmpty()) {
            payableAmounts = mapItemList(payableAmounts, payableAmountsItem);
        }

        List<Map<String, Object>> paymentLocations = getValue(map, responseMapping.get("paymentLocations"),
                LIST_MAP_TYPE);
        Map<String, String> paymentLocationsItem = extractPrefixedMappings(responseMapping, "paymentLocationsItem.");
        if (paymentLocations != null && !paymentLocationsItem.isEmpty()) {
            paymentLocations = mapItemList(paymentLocations, paymentLocationsItem);
        }
        Map<String, String> paymentInstructionsItem = extractPrefixedMappings(responseMapping,
                "paymentInstructionsItem.");
        Map<String, String> howtoPayStepsItem = extractPrefixedMappings(responseMapping, "howtoPayStepsItem.");
        if (paymentLocations != null
                && (!paymentInstructionsItem.isEmpty() || !howtoPayStepsItem.isEmpty())) {
            paymentLocations = mapPaymentLocationsNested(paymentLocations, paymentInstructionsItem, howtoPayStepsItem);
        }

        if (BANK_DEUNA.equalsIgnoreCase(providerName)) {
            String deunaTransactionId = getValue(map, "transactionId", String.class);
            String deunaDeeplink = getValue(map, "deeplink", String.class);
            String deunaQr = getValue(map, "qr", String.class);
            String deunaStatus = getValue(map, "status", String.class);

            // Conserva el mapeo configurable y usa los campos nativos de DEUNA
            // solamente como respaldo cuando el perfil no resolvio alguna clave.
            if (resp.getOperation_id() == null) {
                resp.setOperation_id(deunaTransactionId);
            }
            if (resp.getTransaction_id() == null) {
                resp.setTransaction_id(deunaTransactionId);
            }
            if (resp.getBank_redirect_url() == null) {
                resp.setBank_redirect_url(deunaDeeplink);
            }

            if (payableAmounts == null) {
                payableAmounts = buildPayableAmounts(req);
            }
            if (paymentLocations == null) {
                paymentLocations = buildPaymentLocations(
                        req, "DeUna", resp.getTransaction_id(), resp.getBank_redirect_url(), deunaQr,
                        expirationDatetimeUtc, deunaStatus);
            }
        }

        if (BANK_JEPFASTER.equalsIgnoreCase(providerName)) {
            if (payableAmounts == null) {
                payableAmounts = buildPayableAmounts(req);
            }
            if (paymentLocations == null) {
                paymentLocations = buildPaymentLocations(
                        req, "JEPFaster", resp.getTransaction_id(), null, resp.getBank_redirect_url(),
                        expirationDatetimeUtc, null);
            }
        }

        if (BANK_DEUNA.equalsIgnoreCase(providerName) || BANK_JEPFASTER.equalsIgnoreCase(providerName)) {
            String defaultStepInstruction = getValue(
                    configuredDefaults, HOWTO_PAY_STEP_INSTRUCTION_PATH, String.class);
            paymentLocations = applyHowToPayStepsFallback(paymentLocations, defaultStepInstruction);
        }

        resp.setPayable_amounts(payableAmounts);
        resp.setPayment_locations(paymentLocations);
        resp.setPayment_expiration_datetime(expirationDatetime);
        resp.setPayment_expiration_datetime_utc(expirationDatetimeUtc);

        return resp;
    }

    private List<Map<String, Object>> buildPayableAmounts(DirectOnlinePaymentRequest req) {
        if (req.getSales_amount() == null) {
            return null;
        }

        Map<String, Object> amount = new LinkedHashMap<>();
        if (req.getSales_amount().getValue() != null) {
            amount.put("value", req.getSales_amount().getValue().toPlainString());
        }
        if (req.getSales_amount().getCurrency_code() != null) {
            amount.put("currency_code", req.getSales_amount().getCurrency_code());
        }

        Map<String, Object> payableAmount = new LinkedHashMap<>();
        payableAmount.put("amount", amount);
        return List.of(payableAmount);
    }

    private List<Map<String, Object>> buildPaymentLocations(DirectOnlinePaymentRequest req,
            String locationName, String transactionId, String deeplink, String qr,
            String expirationDatetimeUtc, String status) {
        List<Map<String, Object>> paymentInstructions = new ArrayList<>();
        addPaymentInstruction(paymentInstructions, "TransactionID", transactionId);
        addPaymentInstruction(paymentInstructions, "QRCodeImageBase64", qr);
        addPaymentInstruction(paymentInstructions, "QRCodeUrl", deeplink);
        addPaymentInstruction(paymentInstructions, "QRCodeExpirationTime", expirationDatetimeUtc);

        String locationId = req.getBank_id();
        if (locationId == null || locationId.isBlank()) {
            locationId = String.valueOf(req.getPayment_provider_code());
        }

        Map<String, Object> paymentLocation = new LinkedHashMap<>();
        paymentLocation.put("location_id", locationId);
        paymentLocation.put("location_name", locationName);
        paymentLocation.put("payment_instructions", paymentInstructions);
        paymentLocation.put("howto_pay_steps", List.of());
        if (status != null) {
            paymentLocation.put("status", mapper.convertValue(status, Integer.class));
        }
        return List.of(paymentLocation);
    }

    private void addPaymentInstruction(List<Map<String, Object>> instructions, String name, Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> instruction = new LinkedHashMap<>();
        instruction.put("name", name);
        instruction.put("value", value);
        instruction.put("display_label", "");
        instructions.add(instruction);
    }

    private List<Map<String, Object>> applyHowToPayStepsFallback(List<Map<String, Object>> paymentLocations,
            String stepInstruction) {
        if (paymentLocations == null || stepInstruction == null || stepInstruction.isBlank()) {
            return paymentLocations;
        }

        return paymentLocations.stream().map(location -> {
            Object configuredSteps = location.get("howto_pay_steps");
            if (configuredSteps instanceof List<?> steps && !steps.isEmpty()) {
                return location;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step_number", 1);
            step.put("step_instruction", stepInstruction);

            Map<String, Object> target = new LinkedHashMap<>(location);
            target.put("howto_pay_steps", List.of(step));
            return target;
        }).toList();
    }

    private String buildMissingPointOfSaleMessage(DirectOnlinePaymentRequest req) {
        return "No se ha configurado punto de venta para el local solicitado"
                + " (chain=" + req.getChain()
                + ", store=" + req.getStore()
                + ", pos=" + req.getPos() + ")";
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
