package com.femsa.gpf.pagosdigitales.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationResponse;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.domain.service.SignatureService;
import com.femsa.gpf.pagosdigitales.infrastructure.config.SafetypayConfirmationProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.SafetypayConfirmationProperties.ProviderConfig;
import com.femsa.gpf.pagosdigitales.infrastructure.util.AppUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio de orquestacion para confirmaciones de SafetyPay.
 */
@Log4j2
@Service
public class SafetypayConfirmationService {

    private static final DateTimeFormatter RESPONSE_DATETIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final SafetypayConfirmationProperties properties;
    private final SignatureService signatureService;
    private final SafetypayNotificationStore notificationStore;
    private final ObjectMapper objectMapper;
    private final ProvidersPayService providersPayService;

    /**
     * Crea el servicio con dependencias configuradas.
     *
     * @param properties configuracion de SafetyPay
     * @param signatureService servicio de firma
     * @param notificationStore store de idempotencia
     * @param objectMapper serializador JSON
     * @param providersPayService servicio de proveedores de pago
     */
    public SafetypayConfirmationService(SafetypayConfirmationProperties properties,
            SignatureService signatureService,
            SafetypayNotificationStore notificationStore,
            ObjectMapper objectMapper,
            ProvidersPayService providersPayService) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.notificationStore = notificationStore;
        this.objectMapper = objectMapper;
        this.providersPayService = providersPayService;
    }

    /**
     * Procesa una confirmacion SafetyPay y devuelve el CSV firmado.
     *
     * @param req request de confirmacion
     * @param remoteAddr IP remota del request
     * @return response de confirmacion
     */
    public SafetypayConfirmationResponse handleConfirmation(SafetypayConfirmationRequest req, String remoteAddr) {
        SafetypayConfirmationResponse response = baseResponse(req);

        if (!properties.isEnabled()) {
            return signResponse(response, 3);
        }

        ProviderConfig providerConfig = resolveProviderConfig(req.getPayment_provider_code());
        if (providerConfig == null) {
            return signResponse(response, 3);
        }

        if (!hasRequiredFields(req)) {
            return signResponse(response, 3, providerConfig);
        }

        if (!isIpAllowed(providerConfig, remoteAddr)) {
            return signResponse(response, 3, providerConfig);
        }

        if (isBlank(providerConfig.getSecret())) {
            return signResponse(response, 3, providerConfig);
        }

        if (!isApiKeyValid(providerConfig, req.getApiKey())) {
            return signResponse(response, 1, providerConfig);
        }

        if (!isSignatureValid(providerConfig, req)) {
            return signResponse(response, 2, providerConfig);
        }

        Optional<SafetypayNotificationRecord> existing = notificationStore.find(
                req.getMerchantSalesId(),
                req.getReferenceNo(),
                req.getPaymentReferenceNo());
        if (existing.isPresent()) {
            return signResponse(response, 0, providerConfig);
        }

        SafetypayNotificationRecord record = buildRecord(req, req.getMerchantSalesId());
        notificationStore.save(record);
        log.info("Notificacion SafetyPay registrada: {}", AppUtils.formatPayload(record, objectMapper));

        return signResponse(response, 0, providerConfig);
    }

    /**
     * Genera una respuesta firmada ante errores inesperados.
     *
     * @param req request de confirmacion
     * @param errorNumber codigo de error
     * @return response firmado
     */
    public SafetypayConfirmationResponse errorResponse(SafetypayConfirmationRequest req, int errorNumber) {
        ProviderConfig providerConfig = resolveProviderConfig(req.getPayment_provider_code());
        if (providerConfig == null) {
            return signResponse(baseResponse(req), errorNumber);
        }
        return signResponse(baseResponse(req), errorNumber, providerConfig);
    }

    /**
     * Construye el texto base de firma para requests/response usando el orden definido.
     *
     * @param requestDateTime fecha de request o response
     * @param req request de confirmacion
     * @param secret secreto de firma del proveedor
     * @return texto base a firmar
     */
    public String buildSignatureBase(String requestDateTime, SafetypayConfirmationRequest req, String secret) {
        return nullSafe(requestDateTime)
                + nullSafe(req.getMerchantSalesId())
                + nullSafe(req.getReferenceNo())
                + nullSafe(req.getCreationDateTime())
                + nullSafe(req.getAmount())
                + nullSafe(req.getCurrencyId())
                + nullSafe(req.getPaymentReferenceNo())
                + nullSafe(req.getStatus())
                + nullSafe(secret);
    }

    private ProviderConfig resolveProviderConfig(Integer paymentProviderCode) {
        if (paymentProviderCode == null) {
            return null;
        }
        String providerName = providersPayService.getProviderNameByCode(paymentProviderCode);
        if ("without-provider".equals(providerName)) {
            return null;
        }
        return properties.getProviders().get(providerName);
    }

    private boolean isApiKeyValid(ProviderConfig config, String apiKey) {
        if (config.getApiKey() == null) {
            return false;
        }
        return config.getApiKey().equals(apiKey);
    }

    private boolean isSignatureValid(ProviderConfig config, SafetypayConfirmationRequest req) {
        String base = buildSignatureBase(req.getRequestDateTime(), req, config.getSecret());
        String expected = signatureService.sha256Hex(base);
        return signatureService.isValid(expected, req.getSignature());
    }

    private SafetypayConfirmationResponse baseResponse(SafetypayConfirmationRequest req) {
        SafetypayConfirmationResponse response = new SafetypayConfirmationResponse();
        response.setChannel_POS(req.getChannel_POS());
        response.setMerchantSalesId(req.getMerchantSalesId());
        response.setReferenceNo(req.getReferenceNo());
        response.setCreationDateTime(req.getCreationDateTime());
        response.setAmount(req.getAmount());
        response.setCurrencyId(req.getCurrencyId());
        response.setPaymentReferenceNo(req.getPaymentReferenceNo());
        response.setStatus(req.getStatus());
        response.setOrderNo(req.getMerchantSalesId());
        response.setResponseDateTime(OffsetDateTime.now(ZoneOffset.UTC).format(RESPONSE_DATETIME_FORMAT));
        return response;
    }

    private SafetypayConfirmationResponse signResponse(SafetypayConfirmationResponse response, int errorNumber) {
        response.setErrorNumber(errorNumber);
        String base = nullSafe(response.getResponseDateTime())
                + nullSafe(response.getMerchantSalesId())
                + nullSafe(response.getReferenceNo())
                + nullSafe(response.getCreationDateTime())
                + nullSafe(response.getAmount())
                + nullSafe(response.getCurrencyId())
                + nullSafe(response.getPaymentReferenceNo())
                + nullSafe(response.getStatus())
                + nullSafe("");
        response.setSignature(signatureService.sha256Hex(base));
        return response;
    }

    private SafetypayConfirmationResponse signResponse(SafetypayConfirmationResponse response, int errorNumber,
            ProviderConfig config) {
        response.setErrorNumber(errorNumber);
        String base = nullSafe(response.getResponseDateTime())
                + nullSafe(response.getMerchantSalesId())
                + nullSafe(response.getReferenceNo())
                + nullSafe(response.getCreationDateTime())
                + nullSafe(response.getAmount())
                + nullSafe(response.getCurrencyId())
                + nullSafe(response.getPaymentReferenceNo())
                + nullSafe(response.getStatus())
                + nullSafe(config.getSecret());
        response.setSignature(signatureService.sha256Hex(base));
        return response;
    }

    private SafetypayNotificationRecord buildRecord(SafetypayConfirmationRequest req, String orderNo) {
        SafetypayNotificationRecord record = new SafetypayNotificationRecord();
        record.setPaymentProviderCode(req.getPayment_provider_code());
        record.setProviderName(providersPayService.getProviderNameByCode(req.getPayment_provider_code()));
        record.setMerchantSalesId(req.getMerchantSalesId());
        record.setReferenceNo(req.getReferenceNo());
        record.setPaymentReferenceNo(req.getPaymentReferenceNo());
        record.setStatus(req.getStatus());
        record.setAmount(req.getAmount());
        record.setCurrencyId(req.getCurrencyId());
        record.setSignature(req.getSignature());
        record.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setOrderNo(orderNo);
        record.setRawPayload(AppUtils.formatPayload(buildRawPayload(req), objectMapper));
        return record;
    }

    private boolean hasRequiredFields(SafetypayConfirmationRequest req) {
        return !isBlank(req.getRequestDateTime())
                && !isBlank(req.getMerchantSalesId())
                && !isBlank(req.getReferenceNo())
                && !isBlank(req.getCreationDateTime())
                && !isBlank(req.getAmount())
                && !isBlank(req.getCurrencyId())
                && !isBlank(req.getPaymentReferenceNo())
                && !isBlank(req.getStatus())
                && !isBlank(req.getSignature());
    }

    private Map<String, Object> buildRawPayload(SafetypayConfirmationRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payment_provider_code", req.getPayment_provider_code());
        payload.put("ApiKey", req.getApiKey());
        payload.put("RequestDateTime", req.getRequestDateTime());
        payload.put("MerchantSalesID", req.getMerchantSalesId());
        payload.put("ReferenceNo", req.getReferenceNo());
        payload.put("CreationDateTime", req.getCreationDateTime());
        payload.put("Amount", req.getAmount());
        payload.put("CurrencyID", req.getCurrencyId());
        payload.put("PaymentReferenceNo", req.getPaymentReferenceNo());
        payload.put("Status", req.getStatus());
        payload.put("Signature", req.getSignature());
        return payload;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isIpAllowed(ProviderConfig config, String remoteAddr) {
        if (config.getAllowedIps() == null || config.getAllowedIps().isEmpty()) {
            return true;
        }
        if (isBlank(remoteAddr)) {
            return false;
        }
        return config.getAllowedIps().contains(remoteAddr);
    }
}
