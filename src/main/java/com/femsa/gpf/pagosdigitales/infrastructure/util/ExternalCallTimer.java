package com.femsa.gpf.pagosdigitales.infrastructure.util;

/**
 * Utilidad para medir tiempo transcurrido de llamadas externas.
 */
public final class ExternalCallTimer {

    private final long startNanos;

    private ExternalCallTimer(long startNanos) {
        this.startNanos = startNanos;
    }

    /**
     * Crea un timer iniciado al momento de invocacion.
     *
     * @return timer iniciado
     */
    public static ExternalCallTimer start() {
        return new ExternalCallTimer(System.nanoTime());
    }

    /**
     * Obtiene el tiempo transcurrido en milisegundos.
     *
     * @return tiempo transcurrido en ms
     */
    public Integer elapsedMillis() {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return elapsedMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedMs;
    }
}
