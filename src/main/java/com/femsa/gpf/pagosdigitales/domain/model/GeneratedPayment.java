package com.femsa.gpf.pagosdigitales.domain.model;

import java.time.LocalDateTime;

/**
 * Datos de una forma de pago generada por un proveedor externo.
 *
 * @param chain codigo de cadena
 * @param store codigo de farmacia
 * @param storeName nombre de farmacia
 * @param pos punto de venta
 * @param registrationDate fecha de generacion
 * @param channel canal de origen
 * @param paymentProviderCode codigo del proveedor de pago
 * @param folio folio de la venta del comercio
 * @param externalOperationId identificador generado por el proveedor
 * @param internalSaleId identificador interno de la venta
 */
public record GeneratedPayment(
        Integer chain,
        Integer store,
        String storeName,
        Integer pos,
        LocalDateTime registrationDate,
        String channel,
        Integer paymentProviderCode,
        String folio,
        String externalOperationId,
        String internalSaleId) {
}
