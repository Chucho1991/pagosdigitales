package com.femsa.gpf.pagosdigitales.api.controller;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para invocar rutas Camel por identificador.
 */
@RestController
public class CamelController {

    private final ProducerTemplate producerTemplate;

    /**
     * Crea el controlador con el productor de Camel.
     *
     * @param producerTemplate productor para ejecutar rutas Camel.
     */
    public CamelController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    /**
     * Invoca una ruta Camel con el identificador del proveedor.
     *
     * @param id identificador del proveedor.
     * @return respuesta generada por la ruta Camel.
     */
    @GetMapping("/proveedor/{id}")
    public Object invoke(@PathVariable String id) {
        return producerTemplate.requestBodyAndHeader("direct:proveedor", null, "proveedor", id);
    }

}
