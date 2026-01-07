package com.femsa.gpf.pagosdigitales.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador de endpoints basicos de la API.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/pagos")
public class PagosDigitalesController {

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
        return ResponseEntity.ok(response);
    }
}
