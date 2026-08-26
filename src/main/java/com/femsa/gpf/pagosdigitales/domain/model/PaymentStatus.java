package com.femsa.gpf.pagosdigitales.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Estados normalizados utilizados en las operaciones de pago.
 */
public enum PaymentStatus {

    EXPIRED("100", "Transaction Expired"),
    PENDING("101", "Purchase Pending"),
    PAID("102", "Purchase Complete"),
    COMPLETED("104", "Notification Confirmed");

    private final String code;
    private final String description;

    PaymentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Obtiene el codigo estandar del estado.
     *
     * @return codigo de estado
     */
    public String code() {
        return code;
    }

    /**
     * Obtiene la descripcion estandar compatible con la respuesta de pagos.
     *
     * @return descripcion del estado
     */
    public String description() {
        return description;
    }

    /**
     * Resuelve codigos y estados nativos de los proveedores.
     *
     * @param value codigo o descripcion recibida
     * @return estado normalizado cuando el valor es reconocido
     */
    public static Optional<PaymentStatus> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "100", "EXPIRED", "EXPIRADO" -> Optional.of(EXPIRED);
            case "101", "PENDING", "PENDIENTE" -> Optional.of(PENDING);
            case "102", "PAID", "PAGADO", "APPROVED", "SUCCESS" -> Optional.of(PAID);
            case "104", "COMPLETED", "COMPLETADO" -> Optional.of(COMPLETED);
            default -> Optional.empty();
        };
    }
}
