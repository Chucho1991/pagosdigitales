package com.femsa.gpf.pagosdigitales.application.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

/**
 * Implementacion por defecto que usa MerchantSalesID como OrderNo.
 */
@Log4j2
@Component
public class DefaultSafetypayOrderService implements SafetypayOrderService {

    /**
     * Devuelve el numero de orden usando MerchantSalesID como fallback.
     *
     * @param merchantSalesId identificador del comercio
     * @return numero de orden si existe
     */
    @Override
    public Optional<String> resolveOrderNo(String merchantSalesId) {
        if (merchantSalesId == null || merchantSalesId.isBlank()) {
            return Optional.empty();
        }
        log.warn("Order lookup no implementado, usando MerchantSalesID como OrderNo.");
        return Optional.of(merchantSalesId);
    }

    /**
     * Marca la orden como pagada en el dominio.
     *
     * @param orderNo numero de orden interna
     * @param merchantSalesId identificador del comercio
     */
    @Override
    public void markAsPaid(String orderNo, String merchantSalesId) {
        log.info("Marcando orden como pagada. orderNo={} merchantSalesId={}", orderNo, merchantSalesId);
    }
}
