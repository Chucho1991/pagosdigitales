package com.femsa.gpf.pagosdigitales.api.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.PaymentsProperties;
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

    /**
     * Crea el controlador de pagos con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param paymentsProperties configuracion de proveedores para pagos
     * @param providersPayService servicio de proveedores habilitados
     * @param paymentsMap mapeador de respuestas de pagos
     * @param objectMapper serializador de payloads
     */
    public PaymentsController(ProducerTemplate camel,
            PaymentsProperties paymentsProperties,
            ProvidersPayService providersPayService,
            PaymentsMap paymentsMap,
            ObjectMapper objectMapper) {
        this.camel = camel;
        this.paymentsProperties = paymentsProperties;
        this.providersPayService = providersPayService;
        this.paymentsMap = paymentsMap;
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta pagos por proveedor usando los datos del request.
     *
     * @param req solicitud de pagos
     * @return respuesta con las operaciones de pago
     * @throws IllegalArgumentException cuando falta informacion requerida
     */
    @GetMapping("/payments")
    public PaymentsResponse getPayments(@RequestBody PaymentsRequest req) {
        log.info("Request recibido payments: {}", req);
        if (req.getPayment_provider_code() == null) {
            throw new IllegalArgumentException("payment_provider_code requerido");
        }

        if (req.getOperation_id() == null || req.getOperation_id().isBlank()) {
            throw new IllegalArgumentException("operation_id requerido");
        }

        String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
        log.info("Nombre Proveedor: {}", proveedor);

        if (proveedor.equals("without-provider") || paymentsProperties.getProviders().get(proveedor) == null) {
            throw new IllegalArgumentException("Proveedor no configurado");
        }

        String requestDatetime = req.getRequest_datetime();
        if (requestDatetime == null || requestDatetime.isBlank()) {
            requestDatetime = LocalDateTime.now().format(REQUEST_DATETIME_FORMAT);
        }

        Map<String, Object> camelHeaders = Map.of(
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

        PaymentsResponse response = paymentsMap.mapProviderResponse(req, rawResp, proveedor);
        log.info("Response enviado al cliente payments: {}", response);
        return response;
    }
}
