package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "getbanks")
public class GetBanksProperties {

    private Map<String, ProviderConfig> providers;

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String type;
        private String method;
        private String url;
        private Map<String, String> query;
        private Map<String, String> headers;
    }
}
