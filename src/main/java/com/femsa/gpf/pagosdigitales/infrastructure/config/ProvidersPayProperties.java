package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "providers-pay")
public class ProvidersPayProperties {

    private Map<String, Integer> codes;

    public Map<String, Integer> getCodes() {
        return codes;
    }

    public void setCodes(Map<String, Integer> codes) {
        this.codes = codes;
    }

}
