package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ProviderItem;
import com.femsa.gpf.pagosdigitales.application.mapper.BanksMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.GetBanksProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class BanksController {

    private final ProducerTemplate camel;
    private final GetBanksProperties getBanksprops;
    private final ProvidersPayService providersPayService;
    private final BanksMap banksMap;
    private final ObjectMapper objectMapper;

    public BanksController(ProducerTemplate camel, GetBanksProperties getBanksprops, ProvidersPayService providersPayService,
            BanksMap banksMap, ObjectMapper objectMapper) {
        this.camel = camel;
        this.getBanksprops = getBanksprops;
        this.providersPayService = providersPayService;
        this.banksMap = banksMap;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/banks")
    public BanksResponse getBanks(@RequestBody BanksRequest req) {
        log.info("Request recibido banks: {}", req);

        if (req.getPayment_provider_code() != null) {

            log.info("ID Proveedor: " + req.getPayment_provider_code());

            String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());

            log.info("Nombre Proveedor: " + proveedor);

            if (proveedor.equals("without-provider") || getBanksprops.getProviders().get(proveedor) == null) {
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

            log.info("Response recibido de proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(rawResp, objectMapper));

            // Convertir respuesta
            BanksResponse response = banksMap.mapBanksByProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente banks: {}", response);
            return response;

        } else {
            log.info("No se proporcionó un ID de proveedor");

            Map<String, Integer> listProveedores = providersPayService.getAllProviders();
            List<ProviderItem> listProvidersData = new ArrayList<>();

            if (listProveedores == null || listProveedores.isEmpty()) {
                throw new IllegalArgumentException("Proveedores no configurados");
            }

            if (!listProveedores.isEmpty()) {

                for (Map.Entry<String, Integer> entryProveedor : listProveedores.entrySet()) {

                    String proveedor = entryProveedor.getKey();
                    Integer codProveedor = entryProveedor.getValue();

                    if (getBanksprops.getProviders().get(proveedor) == null) {
                        log.warn("Proveedor no configurado: " + proveedor);
                    } else {

                        log.info("Proveedor configurado: {} → Código: {}", proveedor, codProveedor);

                        try {
                            // Llamar Camel dinámicamente
                            Map<String, Object> camelHeaders = Map.of(
                                    "country_code", req.getCountry_code(),
                                    "now", LocalDateTime.now().toString(),
                                    "getbanks", proveedor
                            );

                            Object rawResp = camel.requestBodyAndHeaders(
                                    "direct:getbanks",
                                    null,
                                    camelHeaders
                            );

                            log.info("Response recibido de proveedor {}: {}", proveedor,
                                    AppUtils.formatPayload(rawResp, objectMapper));

                            // Agregar providerItem solo si hubo respuesta
                            ProviderItem providerItem = new ProviderItem();
                            providerItem.setPayment_provider(entryProveedor);
                            providerItem.setData(rawResp);

                            listProvidersData.add(providerItem);

                        } catch (CamelExecutionException e) {
                            log.error("Error consultando proveedor {} ({}). Continuando con el siguiente. Error: {}",
                                    proveedor, codProveedor, e.getMessage());
                        }
                    }
                }
            }

            // Convertir respuesta
            BanksResponse response = banksMap.mapAllBanksResponse(req, listProvidersData);
            log.info("Response enviado al cliente banks: {}", response);
            return response;
        }
    }

}
