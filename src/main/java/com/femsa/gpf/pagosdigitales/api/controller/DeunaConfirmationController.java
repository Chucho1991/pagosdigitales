package com.femsa.gpf.pagosdigitales.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.DeunaConfirmationRequest;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

import lombok.extern.log4j.Log4j2;

/**
 * Controller para notificaciones de pago (webhook) de Deuna.
 *
 * <p>Deuna envia una notificacion POST cuando un pago se completa exitosamente.
 * Solo notifica pagos exitosos de transacciones de compras.
 * Si la comunicacion falla, Deuna reintenta 3 veces con intervalo de 30 segundos.</p>
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/deuna")
public class DeunaConfirmationController {

    private static final String ESTADO_SUCCESS = "SUCCESS";

    private final IntegrationLogService integrationLogService;
    private final PaymentRegistryService paymentRegistryService;

    /**
     * Crea el controller con sus dependencias.
     *
     * @param integrationLogService servicio de auditoria de logs
     * @param paymentRegistryService servicio de registro de pagos
     */
    public DeunaConfirmationController(IntegrationLogService integrationLogService,
            PaymentRegistryService paymentRegistryService) {
        this.integrationLogService = integrationLogService;
        this.paymentRegistryService = paymentRegistryService;
    }

    /**
     * Recibe notificaciones de pago exitoso desde Deuna.
     *
     * <p>Valida que el status sea "SUCCESS".
     * Actualiza el registro de pago en IN_REGISTRO_PAGOS.</p>
     *
     * @param req payload de confirmacion de Deuna
     * @return respuesta JSON con el resultado del procesamiento
     */
    @PostMapping(value = "/confirmation", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirm(@Valid @RequestBody DeunaConfirmationRequest req) {
        log.info("Notificacion Deuna recibida: idTransaction={}, status={}, transferNumber={}",
                req.getIdTransaction(), req.getStatus(), req.getTransferNumber());

        try {
            // Validar que sea un pago exitoso
            if (!ESTADO_SUCCESS.equalsIgnoreCase(req.getStatus())) {
                log.warn("Notificacion Deuna con status no esperado: {}", req.getStatus());
                logInternal(req, "STATUS_NO_ESPERADO", 400);
                return ResponseEntity.badRequest().body(buildResponse(
                        "RECHAZADO",
                        "Status no esperado: " + req.getStatus()));
            }

            // Actualizar registro de pago
            boolean updated = paymentRegistryService.updateFromDeunaConfirmation(req);

            if (updated) {
                log.info("Pago Deuna confirmado exitosamente. idTransaction={}", req.getIdTransaction());
                logInternal(req, "OK", 200);
                return ResponseEntity.ok(buildResponse("OK", "Pago confirmado exitosamente"));
            } else {
                log.warn("No se encontro registro para confirmar. idTransaction={}", req.getIdTransaction());
                logInternal(req, "REGISTRO_NO_ENCONTRADO", 404);
                return ResponseEntity.status(404).body(buildResponse(
                        "NO_ENCONTRADO",
                        "No se encontro registro de pago para idTransaction: " + req.getIdTransaction()));
            }

        } catch (Exception e) {
            log.error("Error procesando notificacion Deuna. idTransaction={}",
                    req.getIdTransaction(), e);
            logInternal(req, "ERROR_INTERNO", 500);
            return ResponseEntity.internalServerError().body(buildResponse(
                    "ERROR",
                    "Error interno al procesar la notificacion"));
        }
    }

    private java.util.Map<String, String> buildResponse(String estado, String mensaje) {
        return java.util.Map.of("estado", estado, "mensaje", mensaje);
    }

    private void logInternal(DeunaConfirmationRequest req, String message, int status) {
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(req)
                .responsePayload(message)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .codigoProvPago("2")
                .folio(req.getIdTransaction())
                .url("/api/v1/deuna/confirmation")
                .metodo("POST")
                .cpVar1("deuna-confirmation")
                .cpVar2(message)
                .cpVar3(req.getTransferNumber())
                .cpNumber1(status)
                .build());
    }
}
