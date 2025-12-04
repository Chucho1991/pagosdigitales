package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentProviderResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ProviderItem;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;

public class BanksMap {

    private final ProvidersPayService providersPayService;

    public BanksMap(ProvidersPayService providersPayService) {
        this.providersPayService = providersPayService;
    }

    public static BanksResponse mapBanksByProviderResponse(BanksRequest req, Object raw, String providerName) {

        Map<String, Object> map;

        try {
            // Paysafe devuelve byte[]
            if (raw instanceof byte[]) {
                ObjectMapper mapper = new ObjectMapper();
                map = mapper.readValue((byte[]) raw, new TypeReference<Map<String, Object>>() {
                });
            } else if (raw instanceof String) {
                ObjectMapper mapper = new ObjectMapper();
                map = mapper.readValue((String) raw, new TypeReference<Map<String, Object>>() {
                });
            } else {
                map = (Map<String, Object>) raw;
            }

        } catch (Exception e) {
            throw new RuntimeException("Error parseando respuesta de Paysafe", e);
        }

        BanksResponse resp = new BanksResponse();

        // Datos de la solicitud
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());

        // Datos principales del response
        resp.setRequest_id((String) map.get("request_id"));
        resp.setResponse_datetime((String) map.get("response_datetime"));

        // Contenedor de proveedor
        PaymentProviderResponse provider = new PaymentProviderResponse();
        provider.setPayment_provider_name(providerName.toUpperCase());
        provider.setPayment_provider_code(req.getPayment_provider_code());

        // Mapear lista de bancos
        Object banksObj = map.get("banks");
        List<Map<String, Object>> paysafeBanks;
        if (banksObj instanceof List) {
            ObjectMapper mapper = new ObjectMapper();
            paysafeBanks = mapper.convertValue(banksObj, new TypeReference<List<Map<String, Object>>>() {
            });
        } else {
            paysafeBanks = List.of();
        }

        List<BankItem> items = paysafeBanks.stream().map(b -> {

            BankItem bi = new BankItem();

            bi.setBank_id((String) b.get("bank_id"));
            bi.setBank_name((String) b.get("bank_name"));
            bi.setBank_commercial_name((String) b.get("bank_commercial_name"));
            bi.setBank_country_code((String) b.get("bank_country_code"));
            bi.setBank_type((String) b.get("bank_type"));
            bi.setShow_standalone((Boolean) b.get("show_standalone"));
            bi.setChannel((String) b.get("channel"));
            bi.setChannel_tag((String) b.get("channel_tag"));
            bi.setStatus((Integer) b.get("status"));
            bi.setAccess_type((Integer) b.get("access_type"));
            bi.setLanguage_code((String) b.get("language_code"));
            bi.setWorking_hours((String) b.get("working_hours"));
            bi.setDisclaimer((String) b.get("disclaimer"));
            bi.setDefault_currency_code((String) b.get("default_currency_code"));
            bi.setLocations_url((String) b.get("locations_url"));

            // Amount limits → lista de maps
            Object amountLimitsObj = b.get("amount_limits");
            List<Map<String, Object>> amountLimits;
            if (amountLimitsObj instanceof List) {
                ObjectMapper mapper = new ObjectMapper();
                amountLimits = mapper.convertValue(amountLimitsObj, new TypeReference<List<Map<String, Object>>>() {
                });
            } else {
                amountLimits = null;
            }
            bi.setAmount_limits(amountLimits);

            return bi;
        }).toList();

        provider.setBanks(items);

        // Empaquetar proveedor
        resp.setPayment_providers(List.of(provider));

        return resp;
    }

    public static BanksResponse mapAllBanksResponse(BanksRequest req, List<ProviderItem> listaProviderItems) {

        ObjectMapper mapper = new ObjectMapper();

        // ==========================================
        // 1. Convertir cada ProviderItem.data a Map
        // ==========================================
        List<Map<String, Object>> providerMaps = listaProviderItems.stream()
                .map(item -> {

                    Object data = item.getData();

                    try {
                        if (data instanceof byte[]) {
                            return mapper.readValue((byte[]) data, new TypeReference<Map<String, Object>>() {
                            });
                        } else if (data instanceof String) {
                            return mapper.readValue((String) data, new TypeReference<Map<String, Object>>() {
                            });
                        } else if (data instanceof Map) {
                            return (Map<String, Object>) data;
                        } else {
                            return mapper.convertValue(data, new TypeReference<Map<String, Object>>() {
                            });
                        }

                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando data de ProviderItem", e);
                    }

                }).toList();

        // ==========================================
        // 2. Armar BanksResponse
        // ==========================================
        BanksResponse resp = new BanksResponse();
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());

        // request_id y response_datetime vienen del primer provider
        Map<String, Object> first = providerMaps.isEmpty() ? Map.of() : providerMaps.get(0);

        resp.setRequest_id((String) first.get("request_id"));
        resp.setResponse_datetime((String) first.get("response_datetime"));

        // ==========================================
        // 3. Mapear PAYMENT PROVIDERS
        // ==========================================
        List<PaymentProviderResponse> providers = listaProviderItems.stream().map(item -> {

            Map<String, Object> providerMap = providerMaps.get(listaProviderItems.indexOf(item));

            PaymentProviderResponse provider = new PaymentProviderResponse();

            // NOMBRE Y CÓDIGO DESDE providerItem
            provider.setPayment_provider_name(item.getPayment_provider().getKey().toUpperCase());
            provider.setPayment_provider_code(item.getPayment_provider().getValue());

            // ==========================================
            // 4. Mapear banks del provider actual
            // ==========================================
            Object banksObj = providerMap.get("banks");
            List<Map<String, Object>> banksList;

            if (banksObj instanceof List) {
                banksList = mapper.convertValue(banksObj, new TypeReference<List<Map<String, Object>>>() {
                });
            } else {
                banksList = List.of();
            }

            List<BankItem> items = banksList.stream().map(b -> {

                BankItem bi = new BankItem();

                bi.setBank_id((String) b.get("bank_id"));
                bi.setBank_name((String) b.get("bank_name"));
                bi.setBank_commercial_name((String) b.get("bank_commercial_name"));
                bi.setBank_country_code((String) b.get("bank_country_code"));
                bi.setBank_type((String) b.get("bank_type"));
                bi.setShow_standalone((Boolean) b.get("show_standalone"));
                bi.setChannel((String) b.get("channel"));
                bi.setChannel_tag((String) b.get("channel_tag"));
                bi.setStatus((Integer) b.get("status"));
                bi.setAccess_type((Integer) b.get("access_type"));
                bi.setLanguage_code((String) b.get("language_code"));
                bi.setWorking_hours((String) b.get("working_hours"));
                bi.setDisclaimer((String) b.get("disclaimer"));
                bi.setDefault_currency_code((String) b.get("default_currency_code"));
                bi.setLocations_url((String) b.get("locations_url"));

                // amount_limits
                Object amountLimitsObj = b.get("amount_limits");
                List<Map<String, Object>> amountLimits;

                if (amountLimitsObj instanceof List) {
                    amountLimits = mapper.convertValue(amountLimitsObj, new TypeReference<List<Map<String, Object>>>() {
                    });
                } else {
                    amountLimits = List.of();
                }

                bi.setAmount_limits(amountLimits);

                return bi;

            }).toList();

            provider.setBanks(items);

            return provider;

        }).toList();

        resp.setPayment_providers(providers);

        return resp;
    }

}
