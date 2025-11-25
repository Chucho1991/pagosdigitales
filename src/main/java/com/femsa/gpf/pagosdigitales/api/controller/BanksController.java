package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.BanksMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.GetBanksProperties;

import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class BanksController {

    private final ProducerTemplate camel;
    private final GetBanksProperties getBanksprops;
    private final ProvidersPayService providersPayService;

    public BanksController(ProducerTemplate camel, GetBanksProperties getBanksprops, ProvidersPayService providersPayService) {
        this.camel = camel;
        this.getBanksprops = getBanksprops;
        this.providersPayService = providersPayService;
    }

    @PostMapping("/banks")
    public BanksResponse getBanks(@RequestBody BanksRequest req) {

        log.info(req.getPayment_provider_code());

        String proveedor = req.getPayment_provider_code() != null ? providersPayService.getProviderNameByCode(req.getPayment_provider_code()) : "without-provider";

        log.info(proveedor);

        if (proveedor == null || getBanksprops.getProviders().get(proveedor) == null) {
            throw new IllegalArgumentException("Proveedor no configurado");
        }

        // Llamar a Camel dinámicamente
        Map<String, Object> camelHeaders = Map.of(
                "country_code", req.getCountry_code(),
                "now", LocalDateTime.now().toString(),
                "getbanks", proveedor
        );

        log.info(camelHeaders);

        Object rawResp = camel.requestBodyAndHeaders(
                "direct:getbanks",
                null,
                camelHeaders
        );

        // Convertir respuesta
        return BanksMap.mapPaysafeResponse(req, rawResp, proveedor);
    }
}
