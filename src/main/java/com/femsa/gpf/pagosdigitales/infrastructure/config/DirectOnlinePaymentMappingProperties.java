package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuracion de mapeo para pagos en linea directos.
 */
@Data
@Component
@ConfigurationProperties(prefix = "direct-online-payment-requests")
public class DirectOnlinePaymentMappingProperties {

    private Map<String, ProviderMapping> mapping = new HashMap<>();

    /**
     * Obtiene el mapeo asociado a un proveedor o el valor por defecto.
     *
     * @param provider nombre del proveedor
     * @return mapeo para el proveedor indicado
     */
    public ProviderMapping resolve(String provider) {
        return mapping.getOrDefault(provider, mapping.getOrDefault("default", new ProviderMapping()));
    }

    /**
     * Mapeo de request y response para un proveedor.
     */
    @Data
    public static class ProviderMapping {
        private ResponseMapping response = new ResponseMapping();
        private Map<String, String> request = new HashMap<>();
    }

    /**
     * Mapeo de campos de respuesta del proveedor.
     */
    @Data
    public static class ResponseMapping {
        private String responseDatetime = "response_datetime";
        private String operationId = "operation_id";
        private String bankRedirectUrl = "bank_redirect_url";
        private String paymentExpirationDatetime = "payment_expiration_datetime";
        private String paymentExpirationDatetimeUtc = "payment_expiration_datetime_utc";
        private String transactionId = "transaction_id";
        private String payableAmounts = "payable_amounts";
        private String paymentLocations = "payment_locations";
        private Map<String, String> payableAmountsItem = new HashMap<>();
        private Map<String, String> paymentLocationsItem = new HashMap<>();
        private Map<String, String> paymentInstructionsItem = new HashMap<>();
        private Map<String, String> howtoPayStepsItem = new HashMap<>();
    }
}
