package com.femsa.gpf.pagosdigitales.api.controller;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de verificación para rutas Camel.
 */
@RestController
public class TestCamelController {

    private final ProducerTemplate producerTemplate;

    /**
     * Crea el controlador de prueba.
     *
     * @param producerTemplate productor de Camel.
     */
    public TestCamelController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    /**
     * Ejecuta la ruta de prueba para validar Camel.
     *
     * @return mensaje de confirmación de la ruta.
     */
    @GetMapping("/api/v1/camel/test")
    public String testCamel() {
        return producerTemplate.requestBody("direct:test", null, String.class);
    }
}
