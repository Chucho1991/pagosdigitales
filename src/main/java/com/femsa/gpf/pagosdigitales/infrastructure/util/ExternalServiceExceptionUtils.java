package com.femsa.gpf.pagosdigitales.infrastructure.util;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Utilidades para clasificar errores de integracion externa.
 */
public final class ExternalServiceExceptionUtils {

    private ExternalServiceExceptionUtils() {
    }

    /**
     * Determina si una excepcion corresponde a timeout en la llamada externa.
     *
     * @param throwable excepcion a evaluar
     * @return {@code true} si la causa raiz corresponde a timeout
     */
    public static boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            String className = current.getClass().getName();
            if ("org.apache.hc.client5.http.ConnectTimeoutException".equals(className)
                    || className.endsWith(".ConnectTimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
