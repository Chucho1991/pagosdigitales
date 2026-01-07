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
import com.femsa.gpf.pagosdigitales.infrastructure.config.BankMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.BankMappingProperties.ProviderMapping;
import com.femsa.gpf.pagosdigitales.infrastructure.config.BankMappingProperties.ResponseMapping;

/**
 * Mapper para normalizar respuestas de bancos por proveedor.
 */
@Component
public class BanksMap {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper = new ObjectMapper();
    private final BankMappingProperties bankMappingProperties;

    /**
     * Crea el mapper con la configuracion de mapeo.
     *
     * @param bankMappingProperties propiedades de mapeo de bancos
     */
    public BanksMap(BankMappingProperties bankMappingProperties) {
        this.bankMappingProperties = bankMappingProperties;
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
        ProviderMapping mapping = bankMappingProperties.resolve(providerName);

        BanksResponse resp = new BanksResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());

        ResponseMapping responseMapping = mapping.getResponse();
        resp.setRequest_id(getValue(map, responseMapping.getRequestId(), String.class));
        resp.setResponse_datetime(getValue(map, responseMapping.getResponseDatetime(), String.class));

        PaymentProviderResponse provider = new PaymentProviderResponse();
        provider.setPayment_provider_name(providerName.toUpperCase());
        provider.setPayment_provider_code(req.getPayment_provider_code());

        List<BankItem> items = buildBankItems(map, mapping);
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

        List<Map<String, Object>> providerMaps = listaProviderItems.stream()
                .map(item -> toMap(item.getData()))
                .toList();

        if (!providerMaps.isEmpty()) {
            ProviderItem firstItem = listaProviderItems.get(0);
            ProviderMapping firstMapping = bankMappingProperties.resolve(firstItem.getPayment_provider().getKey());
            ResponseMapping responseMapping = firstMapping.getResponse();

            Map<String, Object> first = providerMaps.get(0);
            resp.setRequest_id(getValue(first, responseMapping.getRequestId(), String.class));
            resp.setResponse_datetime(getValue(first, responseMapping.getResponseDatetime(), String.class));
        }

        List<PaymentProviderResponse> providers = new java.util.ArrayList<>();
        for (int i = 0; i < listaProviderItems.size(); i++) {
            ProviderItem item = listaProviderItems.get(i);
            Map<String, Object> providerMap = providerMaps.get(i);

            String providerName = item.getPayment_provider().getKey();
            Integer providerCode = item.getPayment_provider().getValue();
            ProviderMapping mapping = bankMappingProperties.resolve(providerName);

            PaymentProviderResponse provider = new PaymentProviderResponse();
            provider.setPayment_provider_name(providerName.toUpperCase());
            provider.setPayment_provider_code(providerCode);
            provider.setBanks(buildBankItems(providerMap, mapping));

            providers.add(provider);
        }

        resp.setPayment_providers(providers);

        return resp;
    }

    private List<BankItem> buildBankItems(Map<String, Object> providerMap, ProviderMapping mapping) {

        Object banksObj = getValue(providerMap, mapping.getResponse().getBanks(), Object.class);
        List<Map<String, Object>> banksList;

        if (banksObj instanceof List) {
            banksList = mapper.convertValue(banksObj, new TypeReference<List<Map<String, Object>>>() {
            });
        } else {
            banksList = List.of();
        }

        return banksList.stream().map(bank -> {

            BankItem bi = new BankItem();

            mapping.getBank().forEach((targetField, sourceField) -> {
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

    private Map<String, Object> toMap(Object raw) {

        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }

        try {
            if (raw instanceof byte[]) {
                return mapper.readValue((byte[]) raw, MAP_TYPE);
            }
            if (raw instanceof String) {
                return mapper.readValue((String) raw, MAP_TYPE);
            }
            return mapper.convertValue(raw, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error parseando respuesta de proveedor", e);
        }
    }

    private <T> T getValue(Map<String, Object> map, String path, Class<T> clazz) {

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

        return Optional.ofNullable(current)
                .map(clazz::cast)
                .orElse(null);
    }

}
