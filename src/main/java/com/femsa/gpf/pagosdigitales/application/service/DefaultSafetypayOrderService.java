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

    @Override
    public Optional<String> resolveOrderNo(String merchantSalesId) {
        if (merchantSalesId == null || merchantSalesId.isBlank()) {
            return Optional.empty();
        }
        log.warn("Order lookup no implementado, usando MerchantSalesID como OrderNo.");
        return Optional.of(merchantSalesId);
    }

    @Override
    public void markAsPaid(String orderNo, String merchantSalesId) {
        log.info("Marcando orden como pagada. orderNo={} merchantSalesId={}", orderNo, merchantSalesId);
    }
}
