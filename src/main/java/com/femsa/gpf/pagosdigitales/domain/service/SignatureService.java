package com.femsa.gpf.pagosdigitales.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;

/**
 * Servicio para calcular y validar firmas SHA-256 de SafetyPay.
 */
@Service
public class SignatureService {

    /**
     * Calcula la firma SHA-256 sobre el texto base.
     *
     * @param baseText texto base concatenado segun especificacion
     * @return firma en hexadecimal mayuscula
     */
    public String sha256Hex(String baseText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(baseText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * Valida la firma esperada contra la firma recibida.
     *
     * @param expected firma esperada
     * @param received firma recibida
     * @return true si coinciden
     */
    public boolean isValid(String expected, String received) {
        if (expected == null || received == null) {
            return false;
        }
        return expected.equalsIgnoreCase(received.trim());
    }
}
