package com.femsa.gpf.pagosdigitales.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador de endpoints basicos de la API.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/pagos")
public class PagosDigitalesController {

    private final IntegrationLogService integrationLogService;

    /**
     * Crea el controlador base de salud con sus dependencias.
     *
     * @param integrationLogService servicio de auditoria de logs
     */
    public PagosDigitalesController(IntegrationLogService integrationLogService) {
        this.integrationLogService = integrationLogService;
    }

    /**
     * Verifica el estado de la API.
     *
     * @return respuesta de estado en texto plano
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.info("Request recibido pagos/test");
        String response = "Pagos Digitales API OK - Spring Boot 4";
        log.info("Response enviado al cliente pagos/test: {}", response);
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload("ping")
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje("OK")
                .origen("WS_INTERNO")
                .url("/api/v1/pagos/test")
                .metodo("GET")
                .cpVar1("health-check")
                .cpNumber1(200)
                .build());
        return ResponseEntity.ok(response);
    }
}
