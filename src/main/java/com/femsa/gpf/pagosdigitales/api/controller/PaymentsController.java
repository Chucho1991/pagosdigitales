package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ErrorMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.PaymentsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para la consulta de pagos.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class PaymentsController {

    private static final DateTimeFormatter REQUEST_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ProducerTemplate camel;
    private final PaymentsProperties paymentsProperties;
    private final ProvidersPayService providersPayService;
    private final PaymentsMap paymentsMap;
    private final ObjectMapper objectMapper;
    private final ErrorMappingProperties errorMappingProperties;
    private final IntegrationLogService integrationLogService;

    /**
     * Crea el controlador de pagos con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param paymentsProperties configuracion de proveedores para pagos
     * @param providersPayService servicio de proveedores habilitados
     * @param paymentsMap mapeador de respuestas de pagos
     * @param objectMapper serializador de payloads
     * @param errorMappingProperties configuracion de mapeo de errores
     * @param integrationLogService servicio de auditoria de logs
     */
    public PaymentsController(ProducerTemplate camel,
            PaymentsProperties paymentsProperties,
            ProvidersPayService providersPayService,
            PaymentsMap paymentsMap,
            ObjectMapper objectMapper,
            ErrorMappingProperties errorMappingProperties,
            IntegrationLogService integrationLogService) {
        this.camel = camel;
        this.paymentsProperties = paymentsProperties;
        this.providersPayService = providersPayService;
        this.paymentsMap = paymentsMap;
        this.objectMapper = objectMapper;
        this.errorMappingProperties = errorMappingProperties;
        this.integrationLogService = integrationLogService;
    }

    /**
     * Consulta pagos por proveedor usando los datos del request.
     *
     * @param req solicitud de pagos
     * @return respuesta con las operaciones de pago o estructura de error
     * @throws IllegalArgumentException cuando falta informacion requerida
     */
    @GetMapping(value = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPayments(@RequestBody PaymentsRequest req) {
        log.info("Request recibido payments: {}", req);
        String proveedor = null;
        Map<String, Object> camelHeaders = null;
        try {
            if (req.getPayment_provider_code() == null) {
                throw new IllegalArgumentException("payment_provider_code requerido");
            }

            if (req.getOperation_id() == null || req.getOperation_id().isBlank()) {
                throw new IllegalArgumentException("operation_id requerido");
            }

            proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider") || paymentsProperties.getProviders().get(proveedor) == null) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            String requestDatetime = req.getRequest_datetime();
            if (requestDatetime == null || requestDatetime.isBlank()) {
                requestDatetime = LocalDateTime.now().format(REQUEST_DATETIME_FORMAT);
            }

            camelHeaders = Map.of(
                    "payments", proveedor,
                    "operation_id", req.getOperation_id(),
                    "request_datetime", requestDatetime
            );

            Object rawResp = camel.requestBodyAndHeaders(
                    "direct:payments",
                    null,
                    camelHeaders
            );

            log.info("Response recibido de proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(rawResp, objectMapper));

            String errorPath = errorMappingProperties.resolve(proveedor).getError();
            ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
            if (providerError != null) {
                int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                        req.getPos(), req.getPayment_provider_code(), providerError);
                logExternal(req, camelHeaders, errorBody, proveedor, httpCode, "ERROR_PROVEEDOR");
                logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                return ResponseEntity.status(httpCode).body(errorBody);
            }

            PaymentsResponse response = paymentsMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente payments: {}", response);
            logExternal(req, camelHeaders, rawResp, proveedor, 200, "OK");
            logInternal(req, response, 200, "OK");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorInfo error = ApiErrorUtils.invalidRequest(e.getMessage(), null, null, null);
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getPayment_provider_code(), error);
            logInternal(req, errorBody, 400, e.getMessage());
            return ResponseEntity.status(400).body(errorBody);
        } catch (Exception e) {
            log.error("Error procesando payments", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getPayment_provider_code(), error);
            if (proveedor != null) {
                logExternal(req, camelHeaders, errorBody, proveedor, 500, "ERROR_TECNICO");
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
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(req.getOperation_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url("/api/v1/payments")
                .metodo("GET")
                .cpVar1("payments")
                .cpVar2(message)
                .cpNumber1(status)
                .build());
    }

    private void logExternal(PaymentsRequest req, Object outboundBody, Object response, String providerName,
            int status, String message) {
        PaymentsProperties.ProviderConfig providerConfig = paymentsProperties.getProviders().get(providerName);
        integrationLogService.logExternal(IntegrationLogRecord.builder()
                .requestPayload(outboundBody)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen(providerName)
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(req.getOperation_id())
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url(providerConfig == null ? null : providerConfig.getUrl())
                .metodo(providerConfig == null ? null : providerConfig.getMethod())
                .cpVar1("payments")
                .cpVar2(message)
                .cpVar3(providerName)
                .cpNumber1(status)
                .build());
    }
}
