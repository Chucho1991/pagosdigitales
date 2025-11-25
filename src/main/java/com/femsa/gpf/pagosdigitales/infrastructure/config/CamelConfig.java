package com.femsa.gpf.pagosdigitales.infrastructure.config;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfig {

    @Bean
    public CamelContextConfiguration camelContextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext context) {
                context.setTracing(false);
                context.setAllowUseOriginalMessage(true);
                context.setStreamCaching(true);
            }

            @Override
            public void afterApplicationStart(CamelContext context) {}
        };
    }
}

