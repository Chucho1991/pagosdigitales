package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.application.mapper.DirectOnlinePaymentMap;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class DirectOnlinePaymentMapTest {

    @Test
    void mapProviderRequestAddsDefaultsFromDbDefinitions() {
        GatewayWebServiceDefinitionService definitionsService = mock(GatewayWebServiceDefinitionService.class);
        ServiceMappingConfigService serviceMappingConfigService = mock(ServiceMappingConfigService.class);
        Map<String, String> requestMapping = new LinkedHashMap<>();
        requestMapping.put("sales_amount.value", "sales_amount.value");
        when(serviceMappingConfigService.getRequestBodyMappings(
                eq(235689),
                eq("direct-online-payment-requests"),
                eq("paysafe"))).thenReturn(requestMapping);

        when(definitionsService.getDefaults(
                eq(235689),
                eq("direct-online-payment-requests"),
                anyMap())).thenReturn(Map.of(
                        "application_id", "7",
                        "payment_ok_url", "https://www.safetypay.com/success.com",
                        "payment_error_url", "https://www.safetypay.com/error.com"));

        DirectOnlinePaymentMap mapper = new DirectOnlinePaymentMap(
                new ObjectMapper(),
                definitionsService,
                serviceMappingConfigService);

        DirectOnlinePaymentRequest req = new DirectOnlinePaymentRequest();
        req.setPayment_provider_code(235689);
        DirectOnlinePaymentRequest.SalesAmount salesAmount = new DirectOnlinePaymentRequest.SalesAmount();
        salesAmount.setValue(new BigDecimal("50.00"));
        req.setSales_amount(salesAmount);

        Map<String, Object> providerRequest = mapper.mapProviderRequest(req, "paysafe");

        assertThat(providerRequest).containsEntry("application_id", "7");
        assertThat(providerRequest).containsEntry("payment_ok_url", "https://www.safetypay.com/success.com");
        assertThat(providerRequest).containsEntry("payment_error_url", "https://www.safetypay.com/error.com");
        assertThat(providerRequest).containsKey("request_datetime");
        assertThat(((Map<?, ?>) providerRequest.get("sales_amount")).get("value").toString()).isEqualTo("50.00");
    }
}
