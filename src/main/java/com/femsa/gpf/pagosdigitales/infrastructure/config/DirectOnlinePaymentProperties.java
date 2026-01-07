package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Propiedades de configuracion para pagos en linea directos.
 */
@Data
@Component
@ConfigurationProperties(prefix = "direct-online-payment-requests")
public class DirectOnlinePaymentProperties {

    private Map<String, ProviderConfig> providers;

    /**
     * Configuracion especifica de un proveedor.
     */
    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String type;
        private String method;
        private String url;
        private Map<String, String> headers;
        private Defaults defaults = new Defaults();
    }

    /**
     * Valores por defecto para solicitudes de pago.
     */
    @Data
    public static class Defaults {
        private String payment_ok_url;
        private String payment_error_url;
        private String application_id;
        private String merchant_currency_code;
        private Shopper shopper = new Shopper();
    }

    /**
     * Informacion del comprador por defecto.
     */
    @Data
    public static class Shopper {
        private Phone phone = new Phone();
        private String shopper_type;
        private String first_name;
        private String last_names;
        private String tax_id_type;
        private String tax_id;
    }

    /**
     * Informacion de contacto del comprador.
     */
    @Data
    public static class Phone {
        private String phone_type;
        private String phone_country_code;
        private String phone_number;
        private Boolean is_sms_enabled;
    }
}
