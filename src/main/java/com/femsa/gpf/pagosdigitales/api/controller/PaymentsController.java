package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ExternalCallTimer;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para la consulta de pagos.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class PaymentsController {

    private static final String WS_KEY = "payments";
    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ProducerTemplate camel;
    private final ProvidersPayService providersPayService;
    private final PaymentsMap paymentsMap;
    private final ObjectMapper objectMapper;
    private final ServiceMappingConfigService serviceMappingConfigService;
    private final IntegrationLogService integrationLogService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;

    /**
     * Crea el controlador de pagos con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param providersPayService servicio de proveedores habilitados
     * @param paymentsMap mapeador de respuestas de pagos
     * @param objectMapper serializador de payloads
     * @param serviceMappingConfigService servicio de mapeo por BD
     * @param integrationLogService servicio de auditoria de logs
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     */
    public PaymentsController(ProducerTemplate camel,
            ProvidersPayService providersPayService,
            PaymentsMap paymentsMap,
            ObjectMapper objectMapper,
            ServiceMappingConfigService serviceMappingConfigService,
            IntegrationLogService integrationLogService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService) {
        this.camel = camel;
        this.providersPayService = providersPayService;
        this.paymentsMap = paymentsMap;
        this.objectMapper = objectMapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.integrationLogService = integrationLogService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
    }

    /**
     * Consulta pagos por proveedor usando los datos del request.
     *
     * @param req solicitud de pagos
     * @return respuesta con las operaciones de pago o estructura de error
     * @throws IllegalArgumentException cuando falta informacion requerida
     */
    @PostMapping(value = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPayments(@Valid @RequestBody PaymentsRequest req) {
        log.info("Request recibido payments: {}", req);
        req.setChannel_POS(ChannelPosUtils.normalize(req.getChannel_POS()));
        String proveedor = null;
        Map<String, Object> camelHeaders = null;
        Integer externalElapsedMs = null;
        try {
            proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider")
                    || !gatewayWebServiceConfigService.isActive(req.getPayment_provider_code(), WS_KEY)) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            String requestDatetime = req.getRequest_datetime();
            if (requestDatetime == null || requestDatetime.isBlank()) {
                requestDatetime = LocalDateTime.now().format(REQUEST_DATETIME_FORMAT);
            }

            camelHeaders = Map.of(
                    "payments", proveedor,
                    "operation_id", req.getOperation_id(),
                    "request_datetime", requestDatetime,
                    "payment_provider_code", req.getPayment_provider_code()
            );
            final Map<String, Object> headersForProvider = camelHeaders;

            ExternalCallTimer.TimedExecution<Object> timedExecution = ExternalCallTimer.execute(
                    () -> camel.requestBodyAndHeaders(
                            "direct:payments",
                            null,
                            headersForProvider));
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
                        "ERROR_PROVEEDOR", externalElapsedMs);
                logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                return ResponseEntity.status(httpCode).body(errorBody);
            }

            PaymentsResponse response = paymentsMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente payments: {}", response);
            logExternal(req, camelHeaders, rawResp, req.getPayment_provider_code(), proveedor, 200, "OK",
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
            log.error("Error procesando payments", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            if (proveedor != null) {
                logExternal(req, camelHeaders, errorBody, req.getPayment_provider_code(), proveedor, 500,
                        "ERROR_TECNICO", externalElapsedMs);
            }
            logInternal(req, errorBody, 500, "ERROR_INTERNO");
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    private void logInternal(PaymentsRequest req, Object response, int status, String message) {
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(req)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .canal(req.getChannel_POS())
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(req.getOperation_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url("/api/v1/payments")
                .metodo("POST")
                .cpVar1("payments")
                .cpVar2(message)
                .cpNumber1(status)
                .build());
    }

    private void logExternal(PaymentsRequest req, Object outboundBody, Object response, Integer providerCode,
            String providerName, int status, String message, Integer externalElapsedMs) {
        var providerConfig = gatewayWebServiceConfigService.getActiveConfig(providerCode, WS_KEY).orElse(null);
        integrationLogService.logExternal(IntegrationLogRecord.builder()
                .requestPayload(outboundBody)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen(providerName)
                .canal(req.getChannel_POS())
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(req.getOperation_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url(providerConfig == null ? null : providerConfig.uri())
                .metodo(providerConfig == null ? null : providerConfig.method())
                .cpVar1("payments")
                .cpVar2(message)
                .cpVar3(providerName)
                .cpNumber1(status)
                .cpNumber2(externalElapsedMs)
                .build());
    }
}
