package com.femsa.gpf.pagosdigitales.application.mapper;

import java.util.List;
import java.util.Map;

import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentProviderResponse;

public class BanksMap {

    public static BanksResponse mapPaysafeResponse(BanksRequest req, Object raw) {

        Map<String, Object> map = (Map<String, Object>) raw;
    
        BanksResponse resp = new BanksResponse();
    
        resp.setChain(req.getChain());
        resp.setStore(req.getStore());
        resp.setPos(req.getPos());
    
        resp.setRequest_id((String) map.get("request_id"));
        resp.setResponse_datetime((String) map.get("response_datetime"));
    
        // Armamos el contenedor de proveedores
        PaymentProviderResponse provider = new PaymentProviderResponse();
        provider.setPayment_provider_name("PAYSAFE");
        provider.setPayment_provider_code(req.getPayment_provider_code());
    
        // Mapear bancos
        List<Map<String,Object>> paysafeBanks = (List<Map<String,Object>>) map.get("banks");
    
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
            bi.setAmount_limits((List<Map<String, Object>>) b.get("amount_limits"));
            return bi;
        }).toList();
    
        provider.setBanks(items);
    
        resp.setPayment_providers(List.of(provider));
        return resp;
    }
    

}
