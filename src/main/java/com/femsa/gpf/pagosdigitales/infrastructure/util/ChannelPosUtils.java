package com.femsa.gpf.pagosdigitales.infrastructure.util;

/**
 * Utilidades para normalizar el canal de origen de POS.
 */
public final class ChannelPosUtils {

    /**
     * Valor por defecto para canal POS cuando no es informado.
     */
    public static final String DEFAULT_CHANNEL_POS = "POS";

    private ChannelPosUtils() {
    }

    /**
     * Normaliza channel_POS a un valor utilizable.
     *
     * @param channelPos valor recibido en request
     * @return canal recibido o {@value #DEFAULT_CHANNEL_POS} cuando es nulo/vacio
     */
    public static String normalize(String channelPos) {
        if (channelPos == null || channelPos.isBlank()) {
            return DEFAULT_CHANNEL_POS;
        }
        return channelPos;
    }
}
