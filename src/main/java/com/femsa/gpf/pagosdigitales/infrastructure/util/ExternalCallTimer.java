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

    /**
     * Ejecuta una llamada y devuelve su resultado junto al tiempo transcurrido.
     *
     * @param <T> tipo de resultado
     * @param supplier llamada externa a ejecutar
     * @return resultado medido, incluyendo excepcion en caso de fallo
     */
    public static <T> TimedExecution<T> execute(CheckedSupplier<T> supplier) {
        ExternalCallTimer timer = start();
        try {
            return new TimedExecution<>(supplier.get(), timer.elapsedMillis(), null);
        } catch (Exception ex) {
            return new TimedExecution<>(null, timer.elapsedMillis(), ex);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Resultado de una ejecucion temporizada.
     *
     * @param <T> tipo del resultado
     * @param value resultado de la ejecucion
     * @param elapsedMs tiempo transcurrido en ms
     * @param exception excepcion capturada, si existio
     */
    public record TimedExecution<T>(T value, Integer elapsedMs, Exception exception) {
    }
}
