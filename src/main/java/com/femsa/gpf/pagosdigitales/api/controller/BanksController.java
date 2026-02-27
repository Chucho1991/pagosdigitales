package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.api.dto.BanksRequest;
import com.femsa.gpf.pagosdigitales.api.dto.BanksResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ProviderItem;
import com.femsa.gpf.pagosdigitales.application.mapper.BanksMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.BanksCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ExternalCallTimer;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para la consulta de bancos.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class BanksController {

    private static final String WS_KEY = "getbanks";

    private final ProducerTemplate camel;
    private final ProvidersPayService providersPayService;
    private final BanksMap banksMap;
    private final ObjectMapper objectMapper;
    private final ServiceMappingConfigService serviceMappingConfigService;
    private final IntegrationLogService integrationLogService;
    private final BanksCatalogService banksCatalogService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea el controlador de bancos con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param providersPayService servicio de proveedores habilitados
     * @param banksMap mapeador de respuestas de bancos
     * @param objectMapper serializador de payloads
     * @param serviceMappingConfigService servicio de mapeo por BD
     * @param integrationLogService servicio de auditoria de logs
     * @param banksCatalogService servicio de catalogo de bancos por cadena
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public BanksController(ProducerTemplate camel,
            ProvidersPayService providersPayService, BanksMap banksMap, ObjectMapper objectMapper,
            ServiceMappingConfigService serviceMappingConfigService, IntegrationLogService integrationLogService,
            BanksCatalogService banksCatalogService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.camel = camel;
        this.providersPayService = providersPayService;
        this.banksMap = banksMap;
        this.objectMapper = objectMapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.integrationLogService = integrationLogService;
        this.banksCatalogService = banksCatalogService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Consulta bancos por proveedor o para todos los proveedores configurados.
     *
     * @param req solicitud de bancos
     * @return respuesta con los bancos disponibles o estructura de error
     * @throws IllegalArgumentException cuando el proveedor solicitado no esta configurado
     */
    @PostMapping(value = "/banks", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getBanks(@RequestBody BanksRequest req) {
        log.info("Request recibido banks: {}", req);
        req.setChannel_POS(ChannelPosUtils.normalize(req.getChannel_POS()));
        String proveedorSeleccionado = null;
        Map<String, Object> headersProveedor = null;
        Integer externalElapsedMs = null;

        try {
            if (req.getPayment_provider_code() != null) {

                log.info("ID Proveedor: {}", req.getPayment_provider_code());

                String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
                proveedorSeleccionado = proveedor;

                log.info("Nombre Proveedor: {}", proveedor);

                if (proveedor.equals("without-provider")
                        || !gatewayWebServiceConfigService.isActive(req.getPayment_provider_code(), WS_KEY)) {
                    throw new IllegalArgumentException("Proveedor no configurado");
                }

                Map<String, Object> camelHeaders = Map.of(
                        "country_code", req.getCountry_code(),
                        "now", LocalDateTime.now().toString(),
                        "getbanks", proveedor,
                        "payment_provider_code", req.getPayment_provider_code()
                );
                headersProveedor = camelHeaders;

                log.info(camelHeaders);

                ExternalCallTimer.TimedExecution<Object> timedExecution = ExternalCallTimer.execute(
                        () -> camel.requestBodyAndHeaders(
                                "direct:getbanks",
                                null,
                                camelHeaders));
                externalElapsedMs = timedExecution.elapsedMs();
                if (timedExecution.exception() != null) {
                    throw timedExecution.exception();
                }
                Object rawResp = timedExecution.value();

                log.info("Response recibido de proveedor {}: {}", proveedor,
                        AppUtils.formatPayload(rawResp, objectMapper));

                String errorPath = serviceMappingConfigService.getErrorPath(
                        req.getPayment_provider_code(),
                        WS_KEY,
                        proveedor);
                ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
                if (providerError != null) {
                    int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                    Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                            req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), providerError);
                    logExternal(req, camelHeaders, errorBody, req.getPayment_provider_code(), proveedor, httpCode,
                            "ERROR_PROVEEDOR",
                            externalElapsedMs);
                    logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                    return ResponseEntity.status(httpCode).body(errorBody);
                }

                BanksResponse response = banksMap.mapBanksByProviderResponse(req, rawResp, proveedor);
                applyBanksFilter(response, req.getChain(), req.getChannel_POS());
                log.info("Response enviado al cliente banks: {}", response);
                logExternal(req, camelHeaders, rawResp, req.getPayment_provider_code(), proveedor, 200, "OK",
                        externalElapsedMs);
                logInternal(req, response, 200, "OK");
                return ResponseEntity.ok(response);

            } else {
                log.info("No se proporciono payment_provider_code; se ejecuta consulta multi-proveedor.");

                Map<String, Integer> listProveedores = providersPayService.getAllProviders();
                List<ProviderItem> listProvidersData = new ArrayList<>();

                if (listProveedores == null || listProveedores.isEmpty()) {
                    throw new IllegalArgumentException("Proveedores no configurados");
                }

                for (Map.Entry<String, Integer> entryProveedor : listProveedores.entrySet()) {

                    String proveedor = entryProveedor.getKey();
                    Integer codProveedor = entryProveedor.getValue();

                    if (!gatewayWebServiceConfigService.isActive(codProveedor, WS_KEY)) {
                        log.warn("Proveedor no configurado: {}", proveedor);
                    } else {

                        log.info("Proveedor configurado: {} - Codigo: {}", proveedor, codProveedor);

                        try {
                            Map<String, Object> camelHeaders = Map.of(
                                    "country_code", req.getCountry_code(),
                                    "now", LocalDateTime.now().toString(),
                                    "getbanks", proveedor,
                                    "payment_provider_code", codProveedor
                            );

                            ExternalCallTimer.TimedExecution<Object> timedExecution = ExternalCallTimer.execute(
                                    () -> camel.requestBodyAndHeaders(
                                            "direct:getbanks",
                                            null,
                                            camelHeaders));
                            Integer providerElapsedMs = timedExecution.elapsedMs();
                            if (timedExecution.exception() != null) {
                                    if (timedExecution.exception() instanceof CamelExecutionException e) {
                                        log.error("Error consultando proveedor {} ({}). Continuando con el siguiente. Error: {}",
                                                proveedor, codProveedor, e.getMessage());
                                        logExternal(req, null, "Error consultando proveedor: " + e.getMessage(),
                                                codProveedor, proveedor, 500,
                                                "ERROR_CAMEL", providerElapsedMs);
                                        continue;
                                    }
                                    throw timedExecution.exception();
                            }
                            Object rawResp = timedExecution.value();
                            if (rawResp == null) {
                                log.error("Error consultando proveedor {} ({}). Continuando con el siguiente. Error: {}",
                                        proveedor, codProveedor, "Respuesta vacia de proveedor");
                                logExternal(req, null, "Error consultando proveedor: respuesta vacia", codProveedor,
                                        proveedor, 500,
                                        "ERROR_CAMEL", providerElapsedMs);
                                continue;
                            }

                            log.info("Response recibido de proveedor {}: {}", proveedor,
                                    AppUtils.formatPayload(rawResp, objectMapper));

                            ProviderItem providerItem = new ProviderItem();
                            providerItem.setPayment_provider(entryProveedor);
                            providerItem.setData(rawResp);

                            listProvidersData.add(providerItem);
                            logExternal(req, camelHeaders, rawResp, codProveedor, proveedor, 200, "OK",
                                    providerElapsedMs);

                        } catch (Exception e) {
                            log.error("Error consultando proveedor {} ({}). Continuando con el siguiente. Error: {}",
                                    proveedor, codProveedor, e.getMessage());
                            logExternal(req, null, "Error consultando proveedor: " + e.getMessage(), codProveedor,
                                    proveedor, 500, "ERROR_CAMEL", null);
                        }
                    }
                }

                BanksResponse response = banksMap.mapAllBanksResponse(req, listProvidersData);
                applyBanksFilter(response, req.getChain(), req.getChannel_POS());
                log.info("Response enviado al cliente banks: {}", response);
                logInternal(req, response, 200, "OK_MULTI_PROVIDER");
                return ResponseEntity.ok(response);
            }
        } catch (IllegalArgumentException e) {
            ErrorInfo error = ApiErrorUtils.invalidRequest(e.getMessage(), null, null, null);
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            logInternal(req, errorBody, 400, e.getMessage());
            return ResponseEntity.status(400).body(errorBody);
        } catch (Exception e) {
            log.error("Error procesando banks", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            if (proveedorSeleccionado != null) {
                logExternal(req, headersProveedor, errorBody, req.getPayment_provider_code(), proveedorSeleccionado, 500,
                        "ERROR_TECNICO",
                        externalElapsedMs);
            }
            logInternal(req, errorBody, 500, "ERROR_INTERNO");
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    private void logInternal(BanksRequest req, Object response, int status, String message) {
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(req)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .pais(req.getCountry_code())
                .canal(req.getChannel_POS())
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url("/api/v1/banks")
                .metodo("POST")
                .cpVar1("banks")
                .cpVar2(message)
                .cpNumber1(status)
                .build());
    }

    private void logExternal(BanksRequest req, Object outboundBody, Object response, Integer providerCode,
            String providerName, int status, String message, Integer externalElapsedMs) {
        var providerConfig = gatewayWebServiceConfigService.getActiveConfig(providerCode, WS_KEY).orElse(null);
        integrationLogService.logExternal(IntegrationLogRecord.builder()
                .requestPayload(outboundBody)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen(providerName)
                .pais(req.getCountry_code())
                .canal(req.getChannel_POS())
                .codigoProvPago(providerCode == null ? null : providerCode.toString())
                .nombreFarmacia(req.getStore_name())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url(providerConfig == null ? null : providerConfig.uri())
                .metodo(providerConfig == null ? null : providerConfig.method())
                .cpVar1("banks")
                .cpVar2(message)
                .cpVar3(providerName)
                .cpNumber1(status)
                .cpNumber2(externalElapsedMs)
                .build());
    }

    private void applyBanksFilter(BanksResponse response, Integer chain, String channelPos) {
        if (response == null || response.getPayment_providers() == null) {
            return;
        }
        response.getPayment_providers().forEach(provider -> {
            Integer providerCode = provider.getPayment_provider_code();
            Set<String> allowedBankCodes = banksCatalogService.findAllowedBankCodes(providerCode, chain, channelPos);
            if (provider.getBanks() == null || provider.getBanks().isEmpty()) {
                return;
            }
            List<BankItem> filtered = provider.getBanks().stream()
                    .filter(bank -> bank != null && bank.getBank_id() != null)
                    .filter(bank -> allowedBankCodes.contains(bank.getBank_id().trim()))
                    .collect(Collectors.toList());
            provider.setBanks(filtered);
        });
    }
}
