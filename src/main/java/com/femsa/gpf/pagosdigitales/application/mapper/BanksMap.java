package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentProviderResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ProviderItem;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.JsonPayloadUtils;

/**
 * Mapper para normalizar respuestas de bancos por proveedor.
 */
@Component
public class BanksMap {

    private static final String WS_KEY = "getbanks";
    private final ObjectMapper mapper;
    private final ServiceMappingConfigService serviceMappingConfigService;

    /**
     * Crea el mapper con la configuracion de mapeo.
     *
     * @param mapper serializador JSON
     * @param serviceMappingConfigService servicio de mapeo por BD
     */
    public BanksMap(ObjectMapper mapper, ServiceMappingConfigService serviceMappingConfigService) {
        this.mapper = mapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
    }

    /**
     * Convierte la respuesta de un proveedor al formato interno.
     *
     * @param req solicitud original
     * @param raw respuesta cruda del proveedor
     * @param providerName nombre del proveedor
     * @return respuesta de bancos normalizada
     */
    public BanksResponse mapBanksByProviderResponse(BanksRequest req, Object raw, String providerName) {

        Map<String, Object> map = toMap(raw);
        Map<String, String> responseMapping = serviceMappingConfigService.getResponseBodyMappings(
                req.getPayment_provider_code(),
                WS_KEY,
                providerName);
        Map<String, String> bankFieldMapping = extractPrefixedMappings(responseMapping, "bank.");

        BanksResponse resp = new BanksResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());

        resp.setRequest_id(getValue(map, responseMapping.get("requestId"), String.class));
        resp.setResponse_datetime(getValue(map, responseMapping.get("responseDatetime"), String.class));

        PaymentProviderResponse provider = new PaymentProviderResponse();
        provider.setPayment_provider_name(providerName.toUpperCase());
        provider.setPayment_provider_code(req.getPayment_provider_code());

        List<BankItem> items = buildBankItems(map, responseMapping.get("banks"), bankFieldMapping);
        provider.setBanks(items);

        resp.setPayment_providers(List.of(provider));

        return resp;
    }

    /**
     * Convierte multiples respuestas de proveedores en una respuesta unificada.
     *
     * @param req solicitud original
     * @param listaProviderItems lista de proveedores con sus respuestas
     * @return respuesta consolidada de bancos
     */
    public BanksResponse mapAllBanksResponse(BanksRequest req, List<ProviderItem> listaProviderItems) {

        BanksResponse resp = new BanksResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
        resp.setChannel_POS(req.getChannel_POS());

        List<Map<String, Object>> providerMaps = listaProviderItems.stream()
                .map(item -> toMap(item.getData()))
                .toList();

        if (!providerMaps.isEmpty()) {
            ProviderItem firstItem = listaProviderItems.get(0);
            Map<String, String> firstResponseMapping = serviceMappingConfigService.getResponseBodyMappings(
                    firstItem.getPayment_provider().getValue(),
                    WS_KEY,
                    firstItem.getPayment_provider().getKey());

            Map<String, Object> first = providerMaps.get(0);
            resp.setRequest_id(getValue(first, firstResponseMapping.get("requestId"), String.class));
            resp.setResponse_datetime(getValue(first, firstResponseMapping.get("responseDatetime"), String.class));
        }

        List<PaymentProviderResponse> providers = new java.util.ArrayList<>();
        for (int i = 0; i < listaProviderItems.size(); i++) {
            ProviderItem item = listaProviderItems.get(i);
            Map<String, Object> providerMap = providerMaps.get(i);

            String providerName = item.getPayment_provider().getKey();
            Integer providerCode = item.getPayment_provider().getValue();
            Map<String, String> responseMapping = serviceMappingConfigService.getResponseBodyMappings(
                    providerCode,
                    WS_KEY,
                    providerName);
            Map<String, String> bankFieldMapping = extractPrefixedMappings(responseMapping, "bank.");

            PaymentProviderResponse provider = new PaymentProviderResponse();
            provider.setPayment_provider_name(providerName.toUpperCase());
            provider.setPayment_provider_code(providerCode);
            provider.setBanks(buildBankItems(providerMap, responseMapping.get("banks"), bankFieldMapping));

            providers.add(provider);
        }

        resp.setPayment_providers(providers);

        return resp;
    }

    private List<BankItem> buildBankItems(Map<String, Object> providerMap, String banksPath,
            Map<String, String> fieldMapping) {

        Object banksObj = getValue(providerMap, banksPath, Object.class);
        List<Map<String, Object>> banksList;

        if (banksObj instanceof List) {
            banksList = mapper.convertValue(banksObj, new TypeReference<List<Map<String, Object>>>() {
            });
        } else {
            banksList = List.of();
        }

        return banksList.stream().map(bank -> {

            BankItem bi = new BankItem();

            fieldMapping.forEach((targetField, sourceField) -> {
                Object value = getValue(bank, sourceField, Object.class);

                switch (targetField) {
                    case "bank_id" -> bi.setBank_id((String) value);
                    case "bank_name" -> bi.setBank_name((String) value);
                    case "bank_commercial_name" -> bi.setBank_commercial_name((String) value);
                    case "bank_country_code" -> bi.setBank_country_code((String) value);
                    case "bank_type" -> bi.setBank_type((String) value);
                    case "show_standalone" -> bi.setShow_standalone((Boolean) value);
                    case "channel" -> bi.setChannel((String) value);
                    case "channel_tag" -> bi.setChannel_tag((String) value);
                    case "status" -> bi.setStatus(value instanceof Number ? ((Number) value).intValue() : null);
                    case "access_type" -> bi.setAccess_type(value instanceof Number ? ((Number) value).intValue() : null);
                    case "language_code" -> bi.setLanguage_code((String) value);
                    case "working_hours" -> bi.setWorking_hours((String) value);
                    case "disclaimer" -> bi.setDisclaimer((String) value);
                    case "default_currency_code" -> bi.setDefault_currency_code((String) value);
                    case "locations_url" -> bi.setLocations_url((String) value);
                    case "amount_limits" -> bi.setAmount_limits(value instanceof List ? (List<Map<String, Object>>) value : Collections.emptyList());
                    default -> {
                    }
                }
            });

            return bi;
        }).toList();
    }

    private Map<String, String> extractPrefixedMappings(Map<String, String> mappings, String prefix) {
        Map<String, String> extracted = new java.util.LinkedHashMap<>();
        mappings.forEach((appPath, externalPath) -> {
            if (appPath.startsWith(prefix)) {
                extracted.put(appPath.substring(prefix.length()), externalPath);
            }
        });
        return extracted;
    }

    private Map<String, Object> toMap(Object raw) {
        return JsonPayloadUtils.toMap(raw, mapper, "Error parseando respuesta de proveedor");
    }

    private <T> T getValue(Map<String, Object> map, String path, Class<T> clazz) {
        return Optional.ofNullable(JsonPayloadUtils.getValueByPath(map, path))
                .map(clazz::cast)
                .orElse(null);
    }

}
