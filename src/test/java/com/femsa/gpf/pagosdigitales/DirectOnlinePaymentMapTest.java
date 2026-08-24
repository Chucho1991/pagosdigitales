package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.application.mapper.DirectOnlinePaymentMap;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PointOfSaleConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class DirectOnlinePaymentMapTest {

    @Test
    void mapProviderRequestUsesConfiguredPointOfSaleForDeuna() {
        GatewayWebServiceDefinitionService definitionsService = mock(GatewayWebServiceDefinitionService.class);
        ServiceMappingConfigService mappingService = mock(ServiceMappingConfigService.class);
        PointOfSaleConfigService pointOfSaleConfigService = mock(PointOfSaleConfigService.class);
        when(mappingService.getRequestBodyMappings(
                300002, "direct-online-payment-requests", "deuna"))
                .thenReturn(Map.of("amount", "sales_amount.value"));
        when(mappingService.getRequestBodyDataTypes(
                300002, "direct-online-payment-requests", "deuna"))
                .thenReturn(Map.of("amount", "NUMBER"));
        when(definitionsService.getDefaults(eq(300002), eq("direct-online-payment-requests"), anyMap()))
                .thenReturn(Map.of());
        when(pointOfSaleConfigService.findPointOfSale(300002, 60, 148, 90))
                .thenReturn(Optional.of("5"));

        DirectOnlinePaymentMap paymentMap = new DirectOnlinePaymentMap(
                new ObjectMapper(), definitionsService, mappingService, pointOfSaleConfigService);
        DirectOnlinePaymentRequest req = deunaRequest();

        Map<String, Object> providerRequest = paymentMap.mapProviderRequest(req, "deuna");

        assertThat(providerRequest).containsEntry("pointOfSale", "5");
        assertThat(providerRequest.get("pointOfSale")).isNotEqualTo("148");
    }

    @Test
    void mapProviderRequestRejectsDeunaWhenPointOfSaleIsNotConfigured() {
        GatewayWebServiceDefinitionService definitionsService = mock(GatewayWebServiceDefinitionService.class);
        ServiceMappingConfigService mappingService = mock(ServiceMappingConfigService.class);
        PointOfSaleConfigService pointOfSaleConfigService = mock(PointOfSaleConfigService.class);
        when(mappingService.getRequestBodyMappings(
                300002, "direct-online-payment-requests", "deuna"))
                .thenReturn(Map.of());
        when(mappingService.getRequestBodyDataTypes(
                300002, "direct-online-payment-requests", "deuna"))
                .thenReturn(Map.of());
        when(definitionsService.getDefaults(eq(300002), eq("direct-online-payment-requests"), anyMap()))
                .thenReturn(Map.of());
        when(pointOfSaleConfigService.findPointOfSale(300002, 60, 148, 90))
                .thenReturn(Optional.empty());

        DirectOnlinePaymentMap paymentMap = new DirectOnlinePaymentMap(
                new ObjectMapper(), definitionsService, mappingService, pointOfSaleConfigService);

        assertThatThrownBy(() -> paymentMap.mapProviderRequest(deunaRequest(), "deuna"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se ha configurado punto de venta para el local solicitado "
                        + "(chain=60, store=148, pos=90)");
    }

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
        when(serviceMappingConfigService.getRequestBodyDataTypes(
                eq(235689),
                eq("direct-online-payment-requests"),
                eq("paysafe"))).thenReturn(Map.of("sales_amount.value", "NUMBER"));

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
                serviceMappingConfigService,
                mock(PointOfSaleConfigService.class));

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

    @Test
    void mapProviderRequestConvertsJepAmountToConfiguredString() {
        GatewayWebServiceDefinitionService definitionsService = mock(GatewayWebServiceDefinitionService.class);
        ServiceMappingConfigService mappingService = mock(ServiceMappingConfigService.class);
        when(mappingService.getRequestBodyMappings(
                300001, "direct-online-payment-requests", "jepfaster"))
                .thenReturn(Map.of("monto", "sales_amount.value"));
        when(mappingService.getRequestBodyDataTypes(
                300001, "direct-online-payment-requests", "jepfaster"))
                .thenReturn(Map.of("monto", "STRING"));
        when(definitionsService.getDefaults(eq(300001), eq("direct-online-payment-requests"), anyMap()))
                .thenReturn(Map.of());

        DirectOnlinePaymentMap paymentMap = new DirectOnlinePaymentMap(
                new ObjectMapper(), definitionsService, mappingService,
                mock(PointOfSaleConfigService.class));
        DirectOnlinePaymentRequest req = new DirectOnlinePaymentRequest();
        req.setPayment_provider_code(300001);
        req.setBank_id("300001");
        DirectOnlinePaymentRequest.SalesAmount salesAmount = new DirectOnlinePaymentRequest.SalesAmount();
        salesAmount.setValue(new BigDecimal("57.38"));
        salesAmount.setCurrency_code("USD");
        req.setSales_amount(salesAmount);

        Map<String, Object> providerRequest = paymentMap.mapProviderRequest(req, "jepfaster");

        assertThat(providerRequest.get("monto")).isInstanceOf(String.class).isEqualTo("57.38");
        assertThat(providerRequest).doesNotContainKey("request_datetime");
    }

    @Test
    void mapProviderResponseReadsJepNestedQrUsingDatabaseMappings() {
        GatewayWebServiceDefinitionService definitionsService = mock(GatewayWebServiceDefinitionService.class);
        ServiceMappingConfigService mappingService = mock(ServiceMappingConfigService.class);
        when(mappingService.getResponseBodyMappings(
                300001, "direct-online-payment-requests", "jepfaster"))
                .thenReturn(Map.of(
                        "operationId", "codigoTransaccion",
                        "transactionId", "codigoTransaccion",
                        "bankRedirectUrl", "data.qr"));
        when(definitionsService.getDefaults(eq(300001), eq("direct-online-payment-requests"), anyMap()))
                .thenReturn(Map.of());

        DirectOnlinePaymentMap paymentMap = new DirectOnlinePaymentMap(
                new ObjectMapper(), definitionsService, mappingService,
                mock(PointOfSaleConfigService.class));
        DirectOnlinePaymentRequest req = new DirectOnlinePaymentRequest();
        req.setPayment_provider_code(300001);
        req.setBank_id("300001");
        DirectOnlinePaymentRequest.SalesAmount salesAmount = new DirectOnlinePaymentRequest.SalesAmount();
        salesAmount.setValue(new BigDecimal("57.38"));
        salesAmount.setCurrency_code("USD");
        req.setSales_amount(salesAmount);

        String raw = """
                {
                  "data": {"qr": "iVBORw0KGgoAAA"},
                  "codigoTransaccion": "4",
                  "mensaje": "TRANSACCION REALIZADA CON EXITO",
                  "errores": []
                }
                """;

        var response = paymentMap.mapProviderResponse(req, raw, "jepfaster");

        assertThat(response.getOperation_id()).isEqualTo("4");
        assertThat(response.getTransaction_id()).isEqualTo("4");
        assertThat(response.getBank_redirect_url()).isEqualTo("iVBORw0KGgoAAA");
        assertThat(response.getPayable_amounts()).containsExactly(
                Map.of("amount", Map.of("value", "57.38", "currency_code", "USD")));

        List<Map<String, Object>> paymentLocations = response.getPayment_locations();
        assertThat(paymentLocations).hasSize(1);
        assertThat(paymentLocations.get(0))
                .containsEntry("location_id", "300001")
                .containsEntry("location_name", "JEPFaster")
                .containsEntry("howto_pay_steps", List.of());
        assertThat((List<Map<String, Object>>) paymentLocations.get(0).get("payment_instructions"))
                .extracting(item -> item.get("name"), item -> item.get("value"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TransactionID", "4"),
                        org.assertj.core.groups.Tuple.tuple("QRCodeImageBase64", "iVBORw0KGgoAAA"));
    }

    private DirectOnlinePaymentRequest deunaRequest() {
        DirectOnlinePaymentRequest req = new DirectOnlinePaymentRequest();
        req.setPayment_provider_code(300002);
        req.setChain(60);
        req.setStore(148);
        req.setPos(90);
        DirectOnlinePaymentRequest.SalesAmount salesAmount = new DirectOnlinePaymentRequest.SalesAmount();
        salesAmount.setValue(new BigDecimal("57.38"));
        salesAmount.setCurrency_code("USD");
        req.setSales_amount(salesAmount);
        return req;
    }
}
