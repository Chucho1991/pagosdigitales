package com.femsa.gpf.pagosdigitales.domain.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.infrastructure.config.ProvidersPayProperties;

/**
 * Servicio para resolver codigos y nombres de proveedores de pago.
 */
@Service
public class ProvidersPayService {

    private final ProvidersPayProperties providersPayProperties;

    /**
     * Crea el servicio con propiedades de proveedores.
     *
     * @param providersPayProperties propiedades de proveedores
     */
    public ProvidersPayService(ProvidersPayProperties providersPayProperties) {
        this.providersPayProperties = providersPayProperties;
    }

    /**
     * Busca el nombre del proveedor por su codigo.
     *
     * @param code codigo del proveedor
     * @return nombre del proveedor o "without-provider" si no existe
     */
    public String getProviderNameByCode(Integer code) {
        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("without-provider");
    }

    /**
     * Busca el codigo del proveedor por su nombre.
     *
     * @param name nombre del proveedor
     * @return codigo del proveedor o 0 si no existe
     */
    public Integer getProviderCodeByName(String name) {
        return providersPayProperties.getCodes().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getKey(), name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }

    /**
     * Obtiene el mapa completo de proveedores configurados.
     *
     * @return mapa de proveedor a codigo
     */
    public Map<String, Integer> getAllProviders() {
        return Map.copyOf(providersPayProperties.getCodes());
    }

}
