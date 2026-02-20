package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsResponse;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Controller para notificaciones de eventos del comercio.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/payments/notifications")
public class MerchantEventsController {

    private static final DateTimeFormatter RESPONSE_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IntegrationLogService integrationLogService;
    private final PaymentRegistryService paymentRegistryService;
    private final ProvidersPayService providersPayService;

    /**
     * Crea el controlador de eventos de comercio con sus dependencias.
     *
     * @param integrationLogService servicio de auditoria de logs
     * @param paymentRegistryService servicio de registro de pagos
     * @param providersPayService servicio de proveedores habilitados
     */
    public MerchantEventsController(IntegrationLogService integrationLogService,
            PaymentRegistryService paymentRegistryService,
            ProvidersPayService providersPayService) {
        this.integrationLogService = integrationLogService;
        this.paymentRegistryService = paymentRegistryService;
        this.providersPayService = providersPayService;
    }

    /**
     * Recibe eventos del comercio y devuelve respuesta generica local.
     *
     * @param req request generico
     * @return response generico o estructura de error
     */
    @PostMapping(value = "/merchant-events", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> merchantEvents(@RequestBody MerchantEventsRequest req) {
        log.info("Request recibido merchant-events: {}", req);
        req.setChannel_POS(ChannelPosUtils.normalize(req.getChannel_POS()));
        try {
            if (req.getPayment_provider_code() == null) {
                throw new IllegalArgumentException("payment_provider_code requerido");
            }
            validateMerchantSalesId(req);
            String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            if ("without-provider".equals(proveedor)) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            if (paymentRegistryService.areAllEventsAlreadyRegistered(req)) {
                MerchantEventsResponse response = buildGenericResponse(req);
                log.info("Request idempotente en merchant-events. Se retorna respuesta OK sin reinsercion.");
                logInternal(req, response, 200, "OK_IDEMPOTENTE");
                return ResponseEntity.ok(response);
            }

            String folioConflict = paymentRegistryService.validateFolioUniqueness(req);
            if (folioConflict != null) {
                throw new IllegalArgumentException(folioConflict);
            }
            String conflictMessage = paymentRegistryService.validateOperationIdOwnership(req);
            if (conflictMessage != null) {
                throw new IllegalArgumentException(conflictMessage);
            }

            MerchantEventsResponse response = buildGenericResponse(req);
            paymentRegistryService.registerMerchantEvents(req, 0);
            log.info("Response enviado al cliente merchant-events: {}", response);
            logInternal(req, response, 200, "OK");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorInfo error = ApiErrorUtils.invalidRequest(e.getMessage(), null, null, null);
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            logInternal(req, errorBody, 400, e.getMessage());
            return ResponseEntity.status(400).body(errorBody);
        } catch (Exception e) {
            log.error("Error procesando merchant-events", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                    req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), error);
            logInternal(req, errorBody, 500, "ERROR_INTERNO");
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    private void validateMerchantSalesId(MerchantEventsRequest req) {
        if (req.getMerchant_events() == null || req.getMerchant_events().isEmpty()) {
            throw new IllegalArgumentException("merchant_events requerido");
        }
        boolean invalidMerchantSalesId = req.getMerchant_events().stream()
                .anyMatch(event -> event == null
                        || event.getMerchant_sales_id() == null
                        || event.getMerchant_sales_id().isBlank());
        if (invalidMerchantSalesId) {
            throw new IllegalArgumentException("merchant_sales_id requerido en merchant_events");
        }
    }

    private MerchantEventsResponse buildGenericResponse(MerchantEventsRequest req) {
        MerchantEventsResponse response = new MerchantEventsResponse();
        response.setChain(req.getChain());
        response.setStore(req.getStore());
        response.setPos(req.getPos());
        response.setChannel_POS(req.getChannel_POS());
        response.setPayment_provider_code(req.getPayment_provider_code());
        response.setRequest_id(UUID.randomUUID().toString().replace("-", ""));
        response.setResponse_datetime(LocalDateTime.now().format(RESPONSE_DATETIME_FORMAT));
        return response;
    }

    private void logInternal(MerchantEventsRequest req, Object response, int status, String message) {
        String folio = req.getMerchant_events() == null || req.getMerchant_events().isEmpty()
                ? null
                : req.getMerchant_events().get(0).getMerchant_sales_id();
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(req)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .canal(req.getChannel_POS())
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(folio)
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url("/api/v1/payments/notifications/merchant-events")
                .metodo("POST")
                .cpVar1("merchant-events")
                .cpVar2(message)
                .cpNumber1(status)
                .build());
    }
}
