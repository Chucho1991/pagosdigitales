package com.femsa.gpf.pagosdigitales.api.controller;

import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestCamelController {

    private final ProducerTemplate producerTemplate;

    public TestCamelController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @GetMapping("/api/v1/camel/test")
    public String testCamel() {
        return producerTemplate.requestBody("direct:test", null, String.class);
    }
}