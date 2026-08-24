package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import com.femsa.gpf.pagosdigitales.api.dto.PaymentAmount;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperationActivity;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ErrorMappingCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService.RegisteredPayment;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ExternalCallTimer;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ExternalServiceExceptionUtils;

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
    private final ErrorMappingCatalogService errorMappingCatalogService;
    private final IntegrationLogService integrationLogService;
    private final GatewayWebServiceConfigService gatewayWebServiceConfigService;
    private final PaymentRegistryService paymentRegistryService;

    /**
     * Crea el controlador de pagos con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param providersPayService servicio de proveedores habilitados
     * @param paymentsMap mapeador de respuestas de pagos
     * @param objectMapper serializador de payloads
     * @param serviceMappingConfigService servicio de mapeo por BD
     * @param errorMappingCatalogService servicio de catalogo de mapeo de errores
     * @param integrationLogService servicio de auditoria de logs
     * @param gatewayWebServiceConfigService servicio de configuracion de endpoints por BD
     * @param paymentRegistryService servicio de consulta de pagos registrados
     */
    public PaymentsController(ProducerTemplate camel,
            ProvidersPayService providersPayService,
            PaymentsMap paymentsMap,
            ObjectMapper objectMapper,
            ServiceMappingConfigService serviceMappingConfigService,
            ErrorMappingCatalogService errorMappingCatalogService,
            IntegrationLogService integrationLogService,
            GatewayWebServiceConfigService gatewayWebServiceConfigService,
            PaymentRegistryService paymentRegistryService) {
        this.camel = camel;
        this.providersPayService = providersPayService;
        this.paymentsMap = paymentsMap;
        this.objectMapper = objectMapper;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.errorMappingCatalogService = errorMappingCatalogService;
        this.integrationLogService = integrationLogService;
        this.gatewayWebServiceConfigService = gatewayWebServiceConfigService;
        this.paymentRegistryService = paymentRegistryService;
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
        Map<String, Object> outboundBody = null;
        Integer externalElapsedMs = null;
        Object externalResponse = null;
        boolean internalProvider = false;
        try {
            proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider")) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            var providerConfig = gatewayWebServiceConfigService
                    .getActiveConfig(req.getPayment_provider_code(), WS_KEY)
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no configurado"));

            if (providerConfig.internal()) {
                internalProvider = true;
                return getInternalPayment(req);
            }

            if ("JSON".equalsIgnoreCase(providerConfig.requestType())) {
                outboundBody = paymentsMap.mapProviderRequest(req, proveedor);
                if (outboundBody.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No hay mapeos/defaults para construir el body JSON de payments");
                }
                log.info("Request enviado a proveedor {}: {}", proveedor,
                        AppUtils.formatPayload(outboundBody, objectMapper));
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
            final Map<String, Object> bodyForProvider = outboundBody;

            ExternalCallTimer.TimedExecution<Object> timedExecution = ExternalCallTimer.execute(
                    () -> camel.requestBodyAndHeaders(
                            "direct:payments",
                            bodyForProvider,
                            headersForProvider));
            externalElapsedMs = timedExecution.elapsedMs();
            if (timedExecution.exception() != null) {
                throw timedExecution.exception();
            }
            Object rawResp = timedExecution.value();
            externalResponse = rawResp;

            log.info("Response recibido de proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(rawResp, objectMapper));

            String errorPath = serviceMappingConfigService.getErrorPath(
                    req.getPayment_provider_code(),
                    WS_KEY,
                    proveedor);
            ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
            if (providerError != null) {
                providerError = errorMappingCatalogService.mapProviderError(providerError);
                int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                        req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), providerError);
                logExternal(req, outboundBody == null ? camelHeaders : outboundBody,
                        rawResp, req.getPayment_provider_code(), proveedor, httpCode,
                        "ERROR_PROVEEDOR", externalElapsedMs);
                logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                return ResponseEntity.status(httpCode).body(errorBody);
            }

            PaymentsResponse response = paymentsMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente payments: {}", response);
            logExternal(req, outboundBody == null ? camelHeaders : outboundBody,
                    rawResp, req.getPayment_provider_code(), proveedor, 200, "OK",
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
            boolean timeout = ExternalServiceExceptionUtils.isTimeoutException(e);
            int httpCode = timeout ? 504 : 500;
            String message = timeout
                    ? "Se ha perdido la conexi\u00f3n con el proveedor de billetera de pago externo"
                    : "Internal error: " + extractRootCauseMessage(e);
            String logMessage = timeout ? "ERROR_TIMEOUT" : "ERROR_TECNICO";
            ErrorInfo error = timeout ? ApiErrorUtils.gatewayTimeout(message) : ApiErrorUtils.genericError(500, message);
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            if (proveedor != null && !internalProvider) {
                logExternal(req, outboundBody == null ? camelHeaders : outboundBody,
                        externalResponse == null ? errorBody : externalResponse,
                        req.getPayment_provider_code(), proveedor, httpCode,
                        logMessage, externalElapsedMs);
            }
            logInternal(req, errorBody, httpCode, timeout ? "ERROR_TIMEOUT" : "ERROR_INTERNO");
            return ResponseEntity.status(httpCode).body(errorBody);
        }
    }

    private ResponseEntity<?> getInternalPayment(PaymentsRequest req) {
        return paymentRegistryService.findPaymentStatus(req.getOperation_id(), req.getPayment_provider_code())
                .<ResponseEntity<?>>map(payment -> {
                    PaymentsResponse response = buildInternalPaymentResponse(req, payment);
                    log.info("Response interno payments: {}", response);
                    logInternal(req, response, 200, "OK");
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    ErrorInfo error = new ErrorInfo();
                    error.setHttp_code(404);
                    error.setCode("PAYMENT_NOT_FOUND");
                    error.setCategory("NOT_FOUND_ERROR");
                    error.setMessage("No existe un pago para operation_id " + req.getOperation_id());
                    error.setInformation_link(null);
                    error.setInner_details(List.of());
                    Object errorBody = ApiErrorUtils.buildResponse(
                            req.getChain(), req.getStore(), req.getStore_name(), req.getPos(),
                            req.getChannel_POS(), req.getPayment_provider_code(), error);
                    logInternal(req, errorBody, 404, "PAYMENT_NOT_FOUND");
                    return ResponseEntity.status(404).body(errorBody);
                });
    }

    private PaymentsResponse buildInternalPaymentResponse(PaymentsRequest req, RegisteredPayment payment) {
        PaymentOperationActivity activity = new PaymentOperationActivity();
        LocalDateTime statusDatetime = payment.authorizationDatetime() == null
                ? payment.registrationDatetime()
                : payment.authorizationDatetime();
        activity.setCreation_datetime(formatDateTime(statusDatetime));
        activity.setStatus_code(payment.paymentStatus());
        activity.setStatus_description(isBlank(payment.statusDetail())
                ? payment.paymentStatus()
                : payment.statusDetail());

        PaymentOperation operation = new PaymentOperation();
        operation.setRefunds_related(List.of());
        operation.setCreation_datetime(formatDateTime(payment.registrationDatetime()));
        operation.setOperation_id(payment.operationId());
        operation.setMerchant_sales_id(payment.folio());
        operation.setMerchant_order_id(payment.internalSaleId());
        operation.setPayment_amount(buildPaymentAmount(payment));
        operation.setShopper_amount(buildPaymentAmount(payment));
        operation.setOperation_activities(List.of(activity));
        operation.setPayment_reference_number(payment.referenceNumber());

        PaymentsResponse response = new PaymentsResponse();
        response.setChain(req.getChain());
        response.setStore(req.getStore());
        response.setPos(req.getPos());
        response.setChannel_POS(req.getChannel_POS());
        response.setPayment_provider_code(req.getPayment_provider_code());
        response.setRequest_id(req.getOperation_id());
        response.setResponse_datetime(LocalDateTime.now().format(REQUEST_DATETIME_FORMAT));
        response.setPayment_operations(List.of(operation));
        return response;
    }

    private PaymentAmount buildPaymentAmount(RegisteredPayment payment) {
        if (payment.amount() == null && isBlank(payment.currency())) {
            return null;
        }
        PaymentAmount amount = new PaymentAmount();
        amount.setValue(payment.amount());
        amount.setCurrency_code(payment.currency());
        return amount;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(REQUEST_DATETIME_FORMAT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String extractRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
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
