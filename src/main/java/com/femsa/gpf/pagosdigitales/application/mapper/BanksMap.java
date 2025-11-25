package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentProviderResponse;

public class BanksMap {

    public static BanksResponse mapPaysafeResponse(BanksRequest req, Object raw, String providerName) {

        Map<String, Object> map;

        try {
            // Paysafe devuelve byte[]
            if (raw instanceof byte[]) {
                ObjectMapper mapper = new ObjectMapper();
                map = mapper.readValue((byte[]) raw, new TypeReference<Map<String, Object>>() {});
            } else if (raw instanceof String) {
                ObjectMapper mapper = new ObjectMapper();
                map = mapper.readValue((String) raw, new TypeReference<Map<String, Object>>() {});
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
            paysafeBanks = mapper.convertValue(banksObj, new TypeReference<List<Map<String, Object>>>() {});
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
                amountLimits = mapper.convertValue(amountLimitsObj, new TypeReference<List<Map<String, Object>>>() {});
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

}
