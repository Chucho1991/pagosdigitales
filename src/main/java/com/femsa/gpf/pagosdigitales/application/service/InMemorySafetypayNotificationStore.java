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

    @Override
    public Optional<SafetypayNotificationRecord> find(String merchantSalesId, String referenceNo,
            String paymentReferenceNo) {
        return Optional.ofNullable(store.get(buildKey(merchantSalesId, referenceNo, paymentReferenceNo)));
    }

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
