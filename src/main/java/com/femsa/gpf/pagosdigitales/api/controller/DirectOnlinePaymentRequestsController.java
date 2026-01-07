package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.DirectOnlinePaymentMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class DirectOnlinePaymentRequestsController {

    private final ProducerTemplate camel;
    private final DirectOnlinePaymentProperties props;
    private final ProvidersPayService providersPayService;
    private final DirectOnlinePaymentMap directOnlinePaymentMap;
    private final ObjectMapper objectMapper;

    public DirectOnlinePaymentRequestsController(ProducerTemplate camel,
            DirectOnlinePaymentProperties props,
            ProvidersPayService providersPayService,
            DirectOnlinePaymentMap directOnlinePaymentMap,
            ObjectMapper objectMapper) {
        this.camel = camel;
        this.props = props;
        this.providersPayService = providersPayService;
        this.directOnlinePaymentMap = directOnlinePaymentMap;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/direct-online-payment-requests")
    public DirectOnlinePaymentResponse directOnlinePaymentRequests(@RequestBody DirectOnlinePaymentRequest req) {
        log.info("Request recibido direct-online-payment-requests: {}", req);
        if (req.getPayment_provider_code() == null) {
            throw new IllegalArgumentException("payment_provider_code requerido");
        }

        String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
        log.info("Nombre Proveedor: {}", proveedor);

        if (proveedor.equals("without-provider") || props.getProviders().get(proveedor) == null) {
            throw new IllegalArgumentException("Proveedor no configurado");
        }

        Map<String, Object> outboundBody = directOnlinePaymentMap.mapProviderRequest(req, proveedor);
        log.info("Request enviado a proveedor {}: {}", proveedor, outboundBody);

        Map<String, Object> headers = Map.of(
                "direct-online-payment-requests", proveedor
        );

        Object rawResp = camel.requestBodyAndHeaders(
                "direct:direct-online-payment-requests",
                outboundBody,
                headers
        );

        log.info("Response recibido de proveedor {}: {}", proveedor,
                AppUtils.formatPayload(rawResp, objectMapper));

        DirectOnlinePaymentResponse response = directOnlinePaymentMap.mapProviderResponse(req, rawResp, proveedor);
        log.info("Response enviado al cliente direct-online-payment-requests: {}", response);
        return response;
    }

}
