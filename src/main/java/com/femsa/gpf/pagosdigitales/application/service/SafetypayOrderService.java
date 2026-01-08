package com.femsa.gpf.pagosdigitales.application.service;

import java.util.Optional;

/**
 * Servicio para resolver y actualizar ordenes del dominio.
 */
public interface SafetypayOrderService {

    /**
     * Resuelve el numero de orden interna por MerchantSalesID.
     *
     * @param merchantSalesId identificador del comercio
     * @return numero de orden si existe
     */
    Optional<String> resolveOrderNo(String merchantSalesId);

    /**
     * Marca la orden como pagada cuando aplica.
     *
     * @param orderNo numero de orden interna
     * @param merchantSalesId identificador del comercio
     */
    void markAsPaid(String orderNo, String merchantSalesId);
}
