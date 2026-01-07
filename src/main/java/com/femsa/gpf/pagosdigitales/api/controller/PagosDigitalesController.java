package com.femsa.gpf.pagosdigitales.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador principal para comprobar estado del servicio.
 */
@RestController
@RequestMapping("/api/v1/pagos")
public class PagosDigitalesController {

    /**
     * Verifica el estado básico de la API.
     *
     * @return respuesta con mensaje de estado.
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Pagos Digitales API OK - Spring Boot 4");
    }
}
