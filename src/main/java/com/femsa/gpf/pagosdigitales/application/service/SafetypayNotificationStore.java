package com.femsa.gpf.pagosdigitales.application.service;

import java.util.Optional;

/**
 * Store para control de idempotencia y auditoria de notificaciones.
 */
public interface SafetypayNotificationStore {

    /**
     * Busca un registro existente por su llave natural.
     *
     * @param merchantSalesId id de venta del comercio
     * @param referenceNo referencia del comercio
     * @param paymentReferenceNo referencia de pago
     * @return registro si existe
     */
    Optional<SafetypayNotificationRecord> find(String merchantSalesId, String referenceNo, String paymentReferenceNo);

    /**
     * Guarda un registro de notificacion.
     *
     * @param record registro a guardar
     */
    void save(SafetypayNotificationRecord record);
}
