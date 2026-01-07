package com.femsa.gpf.pagosdigitales.domain.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.infrastructure.config.ProvidersPayProperties;

/**
 * Servicio para resolver códigos y nombres de proveedores de pago.
 */
@Service
public class ProvidersPayService {

    private final ProvidersPayProperties providersPayProperties;

    /**
     * Crea el servicio con las propiedades configuradas.
     *
     * @param providersPayProperties propiedades de proveedores.
     */
    public ProvidersPayService(ProvidersPayProperties providersPayProperties) {
        this.providersPayProperties = providersPayProperties;
    }

    /**
     * Obtiene el nombre del proveedor a partir de su código.
     *
     * @param code código del proveedor.
     * @return nombre del proveedor o {@code without-provider} si no existe.
     */
    public String getProviderNameByCode(Integer code) {

        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> entry.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("without-provider");
    }

    /**
     * Obtiene el código del proveedor a partir del nombre.
     *
     * @param name nombre del proveedor.
     * @return código del proveedor o 0 si no existe.
     */
    public Integer getProviderCodeByName(String name) {

        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> entry.getKey().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }

    /**
     * Devuelve todos los proveedores configurados.
     *
     * @return mapa de proveedores y códigos.
     */
    public Map<String, Integer> getAllProviders() {
        return Map.copyOf(providersPayProperties.getCodes());
    }

}
