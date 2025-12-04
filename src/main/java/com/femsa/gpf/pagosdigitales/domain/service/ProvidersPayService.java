package com.femsa.gpf.pagosdigitales.domain.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.infrastructure.config.ProvidersPayProperties;

@Service
public class ProvidersPayService {

    private final ProvidersPayProperties providersPayProperties;

    public ProvidersPayService(ProvidersPayProperties providersPayProperties) {
        this.providersPayProperties = providersPayProperties;
    }

    public String getProviderNameByCode(Integer code) {

        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("without-provider");
    }

    public Integer getProviderCodeByName(String name) {

        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> entry.getKey().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }

    public Map<String, Integer> getAllProviders() {
        return Map.copyOf(providersPayProperties.getCodes());
    }

}
