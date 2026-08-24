package com.femsa.gpf.pagosdigitales.application.ports.in;

import com.femsa.gpf.pagosdigitales.domain.model.GeneratedPayment;

/**
 * Caso de uso para registrar una forma de pago generada.
 */
public interface RegisterGeneratedPaymentUseCase {

    /**
     * Registra la forma de pago generada por el proveedor.
     *
     * @param payment datos normalizados de la forma de pago
     */
    void register(GeneratedPayment payment);
}
