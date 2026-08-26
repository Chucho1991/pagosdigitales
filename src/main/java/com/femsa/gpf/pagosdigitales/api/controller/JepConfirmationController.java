package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

import lombok.extern.log4j.Log4j2;

/**
 * Controller para notificaciones de pago (webhook) de JEPFaster - Cooperativa JEP.
 *
 * <p>Cooperativa JEP envia una notificacion POST cuando un pago QR se completa
 * exitosamente. Los pagos ya notificados no son reenviados por JEP.</p>
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/jep")
public class JepConfirmationController {

    private static final String ESTADO_PAGADO = "PAGADO";
    private static final String ERROR_CERO = "0";

    private final IntegrationLogService integrationLogService;
    private final PaymentRegistryService paymentRegistryService;

    /**
     * Crea el controller con sus dependencias.
     *
     * @param integrationLogService servicio de auditoria de logs
     * @param paymentRegistryService servicio de registro de pagos
     */
    public JepConfirmationController(IntegrationLogService integrationLogService,
            PaymentRegistryService paymentRegistryService) {
        this.integrationLogService = integrationLogService;
        this.paymentRegistryService = paymentRegistryService;
    }

    /**
     * Recibe notificaciones de pago exitoso desde JEPFaster.
     *
     * <p>Valida que el estado sea "PAGADO" y el campo error sea "0".
     * Actualiza el registro de pago en IN_REGISTRO_PAGOS.</p>
     *
     * @param req payload de confirmacion de JEP
     * @return respuesta JSON con el resultado del procesamiento
     */
    @PostMapping(value = { "/notifyPayment", "/confirmation" }, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> notifyPayment(@Valid @RequestBody JepConfirmationRequest req) {
        log.info("Notificacion JEPFaster recibida: idtransaccion={}, estado={}, nummensaje={}",
                req.getIdtransaccion(), req.getEstado(), req.getNummensaje());

        try {
            // Validar que sea un pago exitoso
            if (!ESTADO_PAGADO.equalsIgnoreCase(req.getEstado())) {
                log.warn("Notificacion JEP con estado no esperado: {}", req.getEstado());
                Map<String, String> response = buildResponse(
                        "ERROR",
                        "Estado no esperado: " + req.getEstado());
                logInternal(req, response, "ESTADO_NO_ESPERADO", 400);
                return ResponseEntity.badRequest().body(response);
            }

            if (req.getError() != null && !ERROR_CERO.equals(req.getError().trim())) {
                log.warn("Notificacion JEP con error != 0: {}", req.getError());
                Map<String, String> response = buildResponse(
                        "ERROR",
                        "Campo error con valor: " + req.getError());
                logInternal(req, response, "ERROR_REPORTADO_POR_JEP", 400);
                return ResponseEntity.badRequest().body(response);
            }

            // Actualizar registro de pago
            boolean updated = paymentRegistryService.updateFromJepConfirmation(req);

            if (updated) {
                log.info("Pago JEP confirmado exitosamente. idtransaccion={}", req.getIdtransaccion());
                Map<String, String> response = buildResponse("OK", null);
                logInternal(req, response, "OK", 200);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No se encontro registro para confirmar. idtransaccion={}", req.getIdtransaccion());
                Map<String, String> response = buildResponse(
                        "ERROR",
                        "No se encontro registro de pago para idtransaccion: " + req.getIdtransaccion());
                logInternal(req, response, "REGISTRO_NO_ENCONTRADO", 404);
                return ResponseEntity.status(404).body(response);
            }

        } catch (Exception e) {
            log.error("Error procesando notificacion JEPFaster. idtransaccion={}",
                    req.getIdtransaccion(), e);
            Map<String, String> response = buildResponse(
                    "ERROR",
                    "Error interno al procesar la notificacion");
            logInternal(req, response, "ERROR_INTERNO", 500);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private Map<String, String> buildResponse(String status, String message) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", status);
        if (message != null) {
            response.put("message", message);
        }
        return response;
    }

    private void logInternal(JepConfirmationRequest req, Object response, String message, int status) {
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(req)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .codigoProvPago("300001")
                .folio(req.getIdtransaccion())
                .url("/api/v1/jep/notifyPayment")
                .metodo("POST")
                .cpVar1("jep-notify-payment")
                .cpVar2(message)
                .cpVar3(req.getNummensaje())
                .cpNumber1(status)
                .build());
    }
}
