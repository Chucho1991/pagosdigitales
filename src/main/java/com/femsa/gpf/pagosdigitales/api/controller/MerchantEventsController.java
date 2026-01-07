package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.MerchantEventsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Controller para notificaciones de eventos del comercio.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/payments/notifications")
public class MerchantEventsController {

    private final ProducerTemplate camel;
    private final MerchantEventsProperties props;
    private final ProvidersPayService providersPayService;
    private final MerchantEventsMap merchantEventsMap;
    private final ObjectMapper objectMapper;

    public MerchantEventsController(ProducerTemplate camel,
            MerchantEventsProperties props,
            ProvidersPayService providersPayService,
            MerchantEventsMap merchantEventsMap,
            ObjectMapper objectMapper) {
        this.camel = camel;
        this.props = props;
        this.providersPayService = providersPayService;
        this.merchantEventsMap = merchantEventsMap;
        this.objectMapper = objectMapper;
    }

    /**
     * Recibe eventos del comercio y los reenvia al proveedor.
     *
     * @param req request generico
     * @return response generico
     */
    @PostMapping("/merchant-events")
    public MerchantEventsResponse merchantEvents(@RequestBody MerchantEventsRequest req) {
        log.info("Request recibido merchant-events: {}", req);
        if (req.getPayment_provider_code() == null) {
            throw new IllegalArgumentException("payment_provider_code requerido");
        }

        String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
        log.info("Nombre Proveedor: {}", proveedor);

        if (proveedor.equals("without-provider") || props.getProviders().get(proveedor) == null) {
            throw new IllegalArgumentException("Proveedor no configurado");
        }

        Map<String, Object> outboundBody = merchantEventsMap.mapProviderRequest(req, proveedor);
        log.info("Request enviado a proveedor {}: {}", proveedor,
                AppUtils.formatPayload(outboundBody, objectMapper));

        Map<String, Object> headers = Map.of(
                "merchant-events", proveedor
        );

        Object rawResp = camel.requestBodyAndHeaders(
                "direct:merchant-events",
                outboundBody,
                headers
        );

        log.info("Response recibido de proveedor {}: {}", proveedor,
                AppUtils.formatPayload(rawResp, objectMapper));

        MerchantEventsResponse response = merchantEventsMap.mapProviderResponse(req, rawResp, proveedor);
        log.info("Response enviado al cliente merchant-events: {}", response);
        return response;
    }
}
