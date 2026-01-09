package com.femsa.gpf.pagosdigitales.application.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Implementacion en memoria para idempotencia de notificaciones.
 */
@Component
public class InMemorySafetypayNotificationStore implements SafetypayNotificationStore {

    private final Map<String, SafetypayNotificationRecord> store = new ConcurrentHashMap<>();

    /**
     * Busca una notificacion previamente almacenada.
     *
     * @param merchantSalesId id de venta del comercio
     * @param referenceNo referencia del comercio
     * @param paymentReferenceNo referencia de pago
     * @return registro si existe
     */
    @Override
    public Optional<SafetypayNotificationRecord> find(String merchantSalesId, String referenceNo,
            String paymentReferenceNo) {
        return Optional.ofNullable(store.get(buildKey(merchantSalesId, referenceNo, paymentReferenceNo)));
    }

    /**
     * Guarda una notificacion para idempotencia.
     *
     * @param record registro de notificacion
     */
    @Override
    public void save(SafetypayNotificationRecord record) {
        store.put(buildKey(record.getMerchantSalesId(), record.getReferenceNo(), record.getPaymentReferenceNo()),
                record);
    }

    private String buildKey(String merchantSalesId, String referenceNo, String paymentReferenceNo) {
        return String.join("|",
                merchantSalesId == null ? "" : merchantSalesId,
                referenceNo == null ? "" : referenceNo,
                paymentReferenceNo == null ? "" : paymentReferenceNo);
    }
}
