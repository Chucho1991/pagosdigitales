package com.femsa.gpf.pagosdigitales.application.service;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.application.ports.in.RegisterGeneratedPaymentUseCase;
import com.femsa.gpf.pagosdigitales.application.ports.out.GeneratedPaymentRegistryPort;
import com.femsa.gpf.pagosdigitales.domain.model.GeneratedPayment;

/**
 * Servicio de aplicacion para registrar formas de pago generadas.
 */
@Service
public class RegisterGeneratedPaymentService implements RegisterGeneratedPaymentUseCase {

    private final GeneratedPaymentRegistryPort registryPort;

    /**
     * Crea el servicio con su puerto de persistencia.
     *
     * @param registryPort puerto de registro de pagos
     */
    public RegisterGeneratedPaymentService(GeneratedPaymentRegistryPort registryPort) {
        this.registryPort = registryPort;
    }

    @Override
    public void register(GeneratedPayment payment) {
        registryPort.save(payment);
    }
}
