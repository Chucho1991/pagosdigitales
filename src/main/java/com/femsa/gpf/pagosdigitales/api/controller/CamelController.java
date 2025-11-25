package com.femsa.gpf.pagosdigitales.api.controller;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CamelController {

    private final ProducerTemplate producerTemplate;

    public CamelController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @GetMapping("/proveedor/{id}")
    public Object invoke(@PathVariable String id) {
        return producerTemplate.requestBodyAndHeader("direct:proveedor", null, "proveedor", id);
    }

}
