package com.femsa.gpf.pagosdigitales.api.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.SafetypayConfirmationResponse;
import com.femsa.gpf.pagosdigitales.application.service.SafetypayConfirmationService;

import lombok.extern.log4j.Log4j2;

/**
 * Controller para confirmaciones (webhook) de SafetyPay.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/safetypay")
public class SafetypayConfirmationController {

    private final SafetypayConfirmationService confirmationService;

    /**
     * Crea el controller con sus dependencias.
     *
     * @param confirmationService servicio de confirmaciones
     */
    public SafetypayConfirmationController(SafetypayConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    /**
     * Recibe notificaciones form-urlencoded y responde con CSV firmado.
     *
     * @param apiKey api key entrante
     * @param paymentProviderCode codigo del proveedor de pago
     * @param requestDateTime fecha de request
     * @param merchantSalesId identificador del comercio
     * @param referenceNo referencia
     * @param creationDateTime fecha de creacion
     * @param amount monto
     * @param currencyId moneda
     * @param paymentReferenceNo referencia de pago
     * @param status estado
     * @param signature firma entrante
     * @param httpRequest request HTTP
     * @return response CSV firmado
     */
    @PostMapping(value = "/confirmation", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> confirm(
            @RequestParam(name = "payment_provider_code", required = false) Integer paymentProviderCode,
            @RequestParam(name = "ApiKey", required = false) String apiKey,
            @RequestParam(name = "RequestDateTime", required = false) String requestDateTime,
            @RequestParam(name = "MerchantSalesID", required = false) String merchantSalesId,
            @RequestParam(name = "ReferenceNo", required = false) String referenceNo,
            @RequestParam(name = "CreationDateTime", required = false) String creationDateTime,
            @RequestParam(name = "Amount", required = false) String amount,
            @RequestParam(name = "CurrencyID", required = false) String currencyId,
            @RequestParam(name = "PaymentReferenceNo", required = false) String paymentReferenceNo,
            @RequestParam(name = "Status", required = false) String status,
            @RequestParam(name = "Signature", required = false) String signature,
            HttpServletRequest httpRequest) {

        SafetypayConfirmationRequest req = new SafetypayConfirmationRequest();
        req.setPayment_provider_code(paymentProviderCode);
        req.setApiKey(apiKey);
        req.setRequestDateTime(requestDateTime);
        req.setMerchantSalesId(merchantSalesId);
        req.setReferenceNo(referenceNo);
        req.setCreationDateTime(creationDateTime);
        req.setAmount(amount);
        req.setCurrencyId(currencyId);
        req.setPaymentReferenceNo(paymentReferenceNo);
        req.setStatus(status);
        req.setSignature(signature);

        try {
            SafetypayConfirmationResponse response = confirmationService.handleConfirmation(req,
                    httpRequest.getRemoteAddr());
            log.info("Confirmation procesada. MerchantSalesID={} ErrorNumber={}",
                    merchantSalesId, response.getErrorNumber());
            return ResponseEntity.ok(response.toCsvLine());
        } catch (Exception e) {
            log.error("Error procesando confirmation. MerchantSalesID={}", merchantSalesId, e);
            SafetypayConfirmationResponse response = confirmationService.errorResponse(req, 3);
            return ResponseEntity.ok(response.toCsvLine());
        }
    }
}
