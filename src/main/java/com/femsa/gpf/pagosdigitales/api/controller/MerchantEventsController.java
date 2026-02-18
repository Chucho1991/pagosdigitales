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
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.MerchantEventsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ErrorMappingProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.MerchantEventsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ChannelPosUtils;

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
    private final ErrorMappingProperties errorMappingProperties;
    private final IntegrationLogService integrationLogService;

    /**
     * Crea el controlador de eventos de comercio con sus dependencias.
     *
     * @param camel motor de envio a rutas Camel
     * @param props configuracion de proveedores para merchant-events
     * @param providersPayService servicio de proveedores habilitados
     * @param merchantEventsMap mapeador de solicitudes y respuestas
     * @param objectMapper serializador de payloads
     * @param errorMappingProperties configuracion de mapeo de errores
     * @param integrationLogService servicio de auditoria de logs
     */
    public MerchantEventsController(ProducerTemplate camel,
            MerchantEventsProperties props,
            ProvidersPayService providersPayService,
            MerchantEventsMap merchantEventsMap,
            ObjectMapper objectMapper,
            ErrorMappingProperties errorMappingProperties,
            IntegrationLogService integrationLogService) {
        this.camel = camel;
        this.props = props;
        this.providersPayService = providersPayService;
        this.merchantEventsMap = merchantEventsMap;
        this.objectMapper = objectMapper;
        this.errorMappingProperties = errorMappingProperties;
        this.integrationLogService = integrationLogService;
    }

    /**
     * Recibe eventos del comercio y los reenvia al proveedor.
     *
     * @param req request generico
     * @return response generico o estructura de error
     */
    @PostMapping(value = "/merchant-events", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> merchantEvents(@RequestBody MerchantEventsRequest req) {
        log.info("Request recibido merchant-events: {}", req);
        req.setChannel_POS(ChannelPosUtils.normalize(req.getChannel_POS()));
        String proveedor = null;
        Map<String, Object> outboundBody = null;
        try {
            if (req.getPayment_provider_code() == null) {
                throw new IllegalArgumentException("payment_provider_code requerido");
            }

            proveedor = providersPayService.getProviderNameByCode(req.getPayment_provider_code());
            log.info("Nombre Proveedor: {}", proveedor);

            if (proveedor.equals("without-provider") || props.getProviders().get(proveedor) == null) {
                throw new IllegalArgumentException("Proveedor no configurado");
            }

            outboundBody = merchantEventsMap.mapProviderRequest(req, proveedor);
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

            String errorPath = errorMappingProperties.resolve(proveedor).getError();
            ErrorInfo providerError = ApiErrorUtils.extractProviderError(rawResp, objectMapper, errorPath);
            if (providerError != null) {
                int httpCode = providerError.getHttp_code() == null ? 400 : providerError.getHttp_code();
                Object errorBody = ApiErrorUtils.buildResponse(req.getChain(), req.getStore(), req.getStore_name(),
                        req.getPos(), req.getChannel_POS(), req.getPayment_provider_code(), providerError);
                logExternal(req, outboundBody, errorBody, proveedor, httpCode, "ERROR_PROVEEDOR");
                logInternal(req, errorBody, httpCode, "ERROR_PROVEEDOR");
                return ResponseEntity.status(httpCode).body(errorBody);
            }

            MerchantEventsResponse response = merchantEventsMap.mapProviderResponse(req, rawResp, proveedor);
            log.info("Response enviado al cliente merchant-events: {}", response);
            logExternal(req, outboundBody, rawResp, proveedor, 200, "OK");
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
            if (proveedor != null) {
                logExternal(req, outboundBody, errorBody, proveedor, 500, "ERROR_TECNICO");
            }
            logInternal(req, errorBody, 500, "ERROR_INTERNO");
            return ResponseEntity.status(500).body(errorBody);
        }
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

    private void logExternal(MerchantEventsRequest req, Object outboundBody, Object response, String providerName,
            int status, String message) {
        String folio = req.getMerchant_events() == null || req.getMerchant_events().isEmpty()
                ? null
                : req.getMerchant_events().get(0).getMerchant_sales_id();
        MerchantEventsProperties.ProviderConfig providerConfig = props.getProviders().get(providerName);
        integrationLogService.logExternal(IntegrationLogRecord.builder()
                .requestPayload(outboundBody)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen(providerName)
                .canal(req.getChannel_POS())
                .codigoProvPago(req.getPayment_provider_code() == null ? null : req.getPayment_provider_code().toString())
                .nombreFarmacia(req.getStore_name())
                .folio(folio)
                .farmacia(req.getStore())
                .cadena(req.getChain())
                .pos(req.getPos())
                .url(providerConfig == null ? null : providerConfig.getUrl())
                .metodo(providerConfig == null ? null : providerConfig.getMethod())
                .cpVar1("merchant-events")
                .cpVar2(message)
                .cpVar3(providerName)
                .cpNumber1(status)
                .build());
    }
}
