package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.Map;

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
import com.femsa.gpf.pagosdigitales.infrastructure.config.DirectOnlinePaymentProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ErrorMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para solicitudes de pago en linea directo.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class DirectOnlinePaymentRequestsController {

    private final ProducerTemplate camel;
    private final DirectOnlinePaymentProperties props;
    private final ProvidersPayService providersPayService;
    private final DirectOnlinePaymentMap directOnlinePaymentMap;
    private final ObjectMapper objectMapper;
    private final ErrorMappingProperties errorMappingProperties;

    /**
     * Crea el controlador de pagos en linea con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param props configuracion de proveedores de pago en linea
     * @param providersPayService servicio de proveedores habilitados
     * @param directOnlinePaymentMap mapeador de solicitudes y respuestas
     * @param objectMapper serializador de payloads
     * @param errorMappingProperties configuracion de mapeo de errores
     */
    public DirectOnlinePaymentRequestsController(ProducerTemplate camel,
            DirectOnlinePaymentProperties props,
            ProvidersPayService providersPayService,
            DirectOnlinePaymentMap directOnlinePaymentMap,
            ObjectMapper objectMapper,
            ErrorMappingProperties errorMappingProperties) {
        this.camel = camel;
        this.props = props;
        this.providersPayService = providersPayService;
        this.directOnlinePaymentMap = directOnlinePaymentMap;
        this.objectMapper = objectMapper;
        this.errorMappingProperties = errorMappingProperties;
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
    public ResponseEntity<?> directOnlinePaymentRequests(@RequestBody DirectOnlinePaymentRequest req) {
        log.info("Request recibido direct-online-payment-requests: {}", req);
        try {
            if (req.getPayment_provider_code() == null) {
                throw new IllegalArgumentException("payment_provider_code requerido");
            }

            String proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider") || props.getProviders().get(proveedor) == null) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            Map<String, Object> outboundBody = directOnlinePaymentMap.mapProviderRequest(req, proveedor);
            log.info("Request enviado a proveedor {}: {}", proveedor,
                    AppUtils.formatPayload(outboundBody, objectMapper));

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

            String errorPath = errorMappingProperties.resolve(proveedor).getError();
            ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
            if (providerError != null) {
                int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                return ResponseEntity.status(httpCode)
                        .body(ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                                req.getPos(), req.getPayment_provider_code(), providerError));
            }

            DirectOnlinePaymentResponse response = directOnlinePaymentMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente direct-online-payment-requests: {}", response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorInfo error = ApiErrorUtils.invalidRequest(e.getMessage(), null, null, null);
            return ResponseEntity.status(400)
                    .body(ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                            req.getPos(), req.getPayment_provider_code(), error));
        } catch (Exception e) {
            log.error("Error procesando direct-online-payment-requests", e);
            ErrorInfo error = ApiErrorUtils.genericError(500, "Internal error");
            return ResponseEntity.status(500)
                    .body(ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                            req.getPos(), req.getPayment_provider_code(), error));
        }
    }

}
