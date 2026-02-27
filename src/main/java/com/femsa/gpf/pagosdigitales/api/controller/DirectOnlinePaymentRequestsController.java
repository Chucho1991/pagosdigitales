package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.apache.camel.ProducerTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.application.mapper.DirectOnlinePaymentMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ErrorMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ExternalCallTimer;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para solicitudes de pago en linea directo.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class DirectOnlinePaymentRequestsController {

    private static final String WS_KEY = "direct-online-payment-requests";

    private final ProducerTemplate camel;
    private final ProvidersPayService providersPayService;
    private final DirectOnlinePaymentMap directOnlinePaymentMap;
    private final ObjectMapper objectMapper;
    private final ErrorMappingProperties errorMappingProperties;
    private final IntegrationLogService integrationLogService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea el controlador de pagos en linea con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param providersPayService servicio de proveedores habilitados
     * @param directOnlinePaymentMap mapeador de solicitudes y respuestas
     * @param objectMapper serializador de payloads
     * @param errorMappingProperties configuracion de mapeo de errores
     * @param integrationLogService servicio de auditoria de logs
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public DirectOnlinePaymentRequestsController(ProducerTemplate camel,
            ProvidersPayService providersPayService,
            DirectOnlinePaymentMap directOnlinePaymentMap,
            ObjectMapper objectMapper,
            ErrorMappingProperties errorMappingProperties,
            IntegrationLogService integrationLogService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.camel = camel;
        this.providersPayService = providersPayService;
        this.directOnlinePaymentMap = directOnlinePaymentMap;
        this.objectMapper = objectMapper;
        this.errorMappingProperties = errorMappingProperties;
        this.integrationLogService = integrationLogService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Envia una solicitud de pago en linea al proveedor configurado.
     *
     * @param req solicitud de pago en linea
     * @return respuesta normalizada del proveedor o estructura de error
     * @throws IllegalArgumentException cuando no se define el proveedor
     */
    @PostMapping(value = "/direct-online-payment-requests", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> directOnlinePaymentRequests(@Valid @RequestBody DirectOnlinePaymentRequest req) {
        log.info("Request recibido direct-online-payment-requests: {}", req);
        req.setChannel_POS(ChannelPosUtils.normalize(req.getChannel_POS()));
        String proveedor = null;
        Map<String, Object> outboundBody = null;
        Integer externalElapsedMs = null;
        try {
            proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider")
                    || !gatewayWebServiceConfigService.isActive(req.getPayment_provider_code(), WS_KEY)) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            outboundBody = directOnlinePaymentMap.mapProviderRequest(req, proveedor);
            log.info("Request enviado a proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(outboundBody, objectMapper));

            Map<String, Object> headers = Map.of(
                    "direct-online-payment-requests", proveedor,
                    "payment_provider_code", req.getPayment_provider_code()
            );
            final Map<String, Object> outboundBodyForProvider = outboundBody;

            ExternalCallTimer.TimedExecution<Object> timedExecution = ExternalCallTimer.execute(
                    () -> camel.requestBodyAndHeaders(
                            "direct:direct-online-payment-requests",
                            outboundBodyForProvider,
                            headers));
            externalElapsedMs = timedExecution.elapsedMs();
            if (timedExecution.exception() != null) {
                throw timedExecution.exception();
            }
            Object rawResp = timedExecution.value();

            log.info("Response recibido de proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(rawResp, objectMapper));

            String errorPath = errorMappingProperties.resolve(proveedor).getError();
            ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
            if (providerError != null) {
                int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                        req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), providerError);
                logExternal(req, outboundBody, errorBody, req.getPayment_provider_code(), proveedor, httpCode,
                        "ERROR_PROVEEDOR", externalElapsedMs);
                logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                return ResponseEntity.status(httpCode).body(errorBody);
            }

            DirectOnlinePaymentResponse response = directOnlinePaymentMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente direct-online-payment-requests: {}", response);
            logExternal(req, outboundBody, rawResp, req.getPayment_provider_code(), proveedor, 200, "OK",
                    externalElapsedMs);
            logInternal(req, response, 200, "OK");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorInfo error = ApiErrorUtils.invalidRequest(e.getMessage(), null, null, null);
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            logInternal(req, errorBody, 400, e.getMessage());
            return ResponseEntity.status(400).body(errorBody);
        } catch (Exception e) {
            log.error("Error procesando direct-online-payment-requests", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            if (proveedor != null) {
                logExternal(req, outboundBody, errorBody, req.getPayment_provider_code(), proveedor, 500,
                        "ERROR_TECNICO", externalElapsedMs);
            }
            logInternal(req, errorBody, 500, "ERROR_INTERNO");
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    private void logInternal(DirectOnlinePaymentRequest req, Object response, int status, String message) {
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
                .folio(req.getMerchant_sales_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url("/api/v1/direct-online-payment-requests")
                .metodo("POST")
                .cpVar1("direct-online-payment-requests")
                .cpVar2(message)
                .cpNumber1(status)
                .build());
    }

    private void logExternal(DirectOnlinePaymentRequest req, Object outboundBody, Object response, Integer providerCode,
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
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(req.getMerchant_sales_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url(providerConfig == null ? null : providerConfig.uri())
                .metodo(providerConfig == null ? null : providerConfig.method())
                .cpVar1("direct-online-payment-requests")
                .cpVar2(message)
                .cpVar3(providerName)
                .cpNumber1(status)
                .cpNumber2(externalElapsedMs)
                .build());
    }
}
