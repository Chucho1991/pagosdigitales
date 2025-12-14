package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "getbanks")
public class BankMappingProperties {

    private Map<String, ProviderMapping> mapping = new HashMap<>();

    public ProviderMapping resolve(String provider) {
        return mapping.getOrDefault(provider, mapping.getOrDefault("default", new ProviderMapping()));
    }

    @Data
    public static class ProviderMapping {
        private ResponseMapping response = new ResponseMapping();
        private Map<String, String> bank = new HashMap<>();
    }

    @Data
    public static class ResponseMapping {
        private String requestId = "request_id";
        private String responseDatetime = "response_datetime";
        private String banks = "banks";
    }
}
