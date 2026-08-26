package com.femsa.gpf.pagosdigitales.application.ports.out;

import java.math.BigDecimal;

/**
 * Puerto para generar identificadores secuenciales enviados a proveedores.
 */
public interface ProviderTransactionSequencePort {

    /**
     * Obtiene el siguiente identificador para una transaccion JEPFaster.
     *
     * @return siguiente valor de la secuencia de JEPFaster
     */
    BigDecimal nextJepTransactionId();

    /**
     * Obtiene el siguiente identificador para una transaccion DEUNA.
     *
     * @return siguiente valor de la secuencia de DEUNA
     */
    BigDecimal nextDeunaTransactionId();
}
