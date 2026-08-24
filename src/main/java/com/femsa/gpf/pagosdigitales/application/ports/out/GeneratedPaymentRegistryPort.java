package com.femsa.gpf.pagosdigitales.application.ports.out;

import com.femsa.gpf.pagosdigitales.domain.model.GeneratedPayment;

/**
 * Puerto de salida para persistir formas de pago generadas.
 */
public interface GeneratedPaymentRegistryPort {

    /**
     * Persiste una forma de pago generada.
     *
     * @param payment datos normalizados de la forma de pago
     */
    void save(GeneratedPayment payment);
}
