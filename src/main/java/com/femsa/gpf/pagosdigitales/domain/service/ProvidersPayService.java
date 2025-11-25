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
        
        // Convertimos el entero a String porque el YAML siempre queda como String
        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("without-provider");
    }
}
