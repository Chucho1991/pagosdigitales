package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Ruta Camel de prueba para verificar ejecucion de rutas.
 */
@Component
public class TestRoute extends RouteBuilder {

    /**
     * Configura la ruta de prueba.
     */
    @Override
    public void configure() {

        from("direct:test")
                .routeId("testRoute")
                .log("➡️ [Camel] Ejecutando ruta de prueba...")
                .setBody(constant("Ruta Camel ejecutada correctamente"))
                .log("✔️ [Camel] Finalizó ruta de prueba.");
    }
}
