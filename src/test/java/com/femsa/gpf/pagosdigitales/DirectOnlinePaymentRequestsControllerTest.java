package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.controller.DirectOnlinePaymentRequestsController;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentRequest;
import com.femsa.gpf.pagosdigitales.api.dto.DirectOnlinePaymentResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.application.mapper.DirectOnlinePaymentMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.BanksCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ErrorMappingCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class DirectOnlinePaymentRequestsControllerTest {

    @Test
    void directOnlinePaymentRequestsReturnsMappedErrorWhenAmountIsBelowConfiguredMinimum() {
        ProducerTemplate camel = mock(ProducerTemplate.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        DirectOnlinePaymentMap directOnlinePaymentMap = mock(DirectOnlinePaymentMap.class);
        ServiceMappingConfigService serviceMappingConfigService = mock(ServiceMappingConfigService.class);
        ErrorMappingCatalogService errorMappingCatalogService = mock(ErrorMappingCatalogService.class);
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        GatewayWebServiceConfigService gatewayWebServiceConfigService = mock(GatewayWebServiceConfigService.class);
        BanksCatalogService banksCatalogService = mock(BanksCatalogService.class);

        when(providersPayService.getProviderNameByCode(235689)).thenReturn("paysafe");
        when(gatewayWebServiceConfigService.isActive(235689, "direct-online-payment-requests")).thenReturn(true);
        when(banksCatalogService.findMinimum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("20.00")));
        when(banksCatalogService.findMaximum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("100.00")));

        ErrorInfo mappedError = new ErrorInfo();
        mappedError.setHttp_code(400);
        mappedError.setCode("1004");
        mappedError.setCategory("INVALID_REQUEST_ERROR");
        mappedError.setMessage("El monto no cumple el minimo.");
        mappedError.setInner_details(java.util.List.of());
        when(errorMappingCatalogService.buildErrorByCurrentCode(1004L)).thenReturn(mappedError);

        DirectOnlinePaymentRequestsController controller = new DirectOnlinePaymentRequestsController(
                camel,
                providersPayService,
                directOnlinePaymentMap,
                new ObjectMapper(),
                serviceMappingConfigService,
                errorMappingCatalogService,
                integrationLogService,
                gatewayWebServiceConfigService,
                banksCatalogService);

        DirectOnlinePaymentRequest request = buildRequest(new BigDecimal("10.00"));

        ResponseEntity<?> response = controller.directOnlinePaymentRequests(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getError().getCode()).isEqualTo("1004");
        assertThat(body.getError().getMessage()).isEqualTo("El monto no cumple el minimo.");
        verify(camel, never()).requestBodyAndHeaders(anyString(), any(), anyMap());
        verify(directOnlinePaymentMap, never()).mapProviderRequest(any(), any());
    }

    @Test
    void directOnlinePaymentRequestsDelegatesToProviderWhenConfiguredMinimumIsMet() {
        ProducerTemplate camel = mock(ProducerTemplate.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        DirectOnlinePaymentMap directOnlinePaymentMap = mock(DirectOnlinePaymentMap.class);
        ServiceMappingConfigService serviceMappingConfigService = mock(ServiceMappingConfigService.class);
        ErrorMappingCatalogService errorMappingCatalogService = mock(ErrorMappingCatalogService.class);
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        GatewayWebServiceConfigService gatewayWebServiceConfigService = mock(GatewayWebServiceConfigService.class);
        BanksCatalogService banksCatalogService = mock(BanksCatalogService.class);

        when(providersPayService.getProviderNameByCode(235689)).thenReturn("paysafe");
        when(gatewayWebServiceConfigService.isActive(235689, "direct-online-payment-requests")).thenReturn(true);
        when(banksCatalogService.findMinimum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("20.00")));
        when(banksCatalogService.findMaximum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("100.00")));
        when(directOnlinePaymentMap.mapProviderRequest(any(), eq("paysafe"))).thenReturn(Map.of("amount", 25));
        when(camel.requestBodyAndHeaders(eq("direct:direct-online-payment-requests"), eq(Map.of("amount", 25)), anyMap()))
                .thenReturn(Map.of("provider", "ok"));
        when(serviceMappingConfigService.getErrorPath(235689, "direct-online-payment-requests", "paysafe"))
                .thenReturn("error");

        DirectOnlinePaymentResponse providerResponse = new DirectOnlinePaymentResponse();
        providerResponse.setOperation_id("OP-1");
        when(directOnlinePaymentMap.mapProviderResponse(any(), eq(Map.of("provider", "ok")), eq("paysafe")))
                .thenReturn(providerResponse);

        DirectOnlinePaymentRequestsController controller = new DirectOnlinePaymentRequestsController(
                camel,
                providersPayService,
                directOnlinePaymentMap,
                new ObjectMapper(),
                serviceMappingConfigService,
                errorMappingCatalogService,
                integrationLogService,
                gatewayWebServiceConfigService,
                banksCatalogService);

        DirectOnlinePaymentRequest request = buildRequest(new BigDecimal("25.00"));

        ResponseEntity<?> response = controller.directOnlinePaymentRequests(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(providerResponse);
        verify(camel).requestBodyAndHeaders(eq("direct:direct-online-payment-requests"), eq(Map.of("amount", 25)), anyMap());
    }

    @Test
    void directOnlinePaymentRequestsLogsRawProviderResponseWhenProviderReturnsMappedError() {
        ProducerTemplate camel = mock(ProducerTemplate.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        DirectOnlinePaymentMap directOnlinePaymentMap = mock(DirectOnlinePaymentMap.class);
        ServiceMappingConfigService serviceMappingConfigService = mock(ServiceMappingConfigService.class);
        ErrorMappingCatalogService errorMappingCatalogService = mock(ErrorMappingCatalogService.class);
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        GatewayWebServiceConfigService gatewayWebServiceConfigService = mock(GatewayWebServiceConfigService.class);
        BanksCatalogService banksCatalogService = mock(BanksCatalogService.class);

        Map<String, Object> providerRequest = Map.of("amount", 25);
        Map<String, Object> rawProviderError = Map.of("error", Map.of("code", "EXT-1", "message", "declined"));

        when(providersPayService.getProviderNameByCode(235689)).thenReturn("paysafe");
        when(gatewayWebServiceConfigService.isActive(235689, "direct-online-payment-requests")).thenReturn(true);
        when(banksCatalogService.findMinimum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("20.00")));
        when(banksCatalogService.findMaximum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("100.00")));
        when(directOnlinePaymentMap.mapProviderRequest(any(), eq("paysafe"))).thenReturn(providerRequest);
        when(camel.requestBodyAndHeaders(eq("direct:direct-online-payment-requests"), eq(providerRequest), anyMap()))
                .thenReturn(rawProviderError);
        when(serviceMappingConfigService.getErrorPath(235689, "direct-online-payment-requests", "paysafe"))
                .thenReturn("error");

        ErrorInfo mappedError = new ErrorInfo();
        mappedError.setHttp_code(409);
        mappedError.setCode("PD-409");
        mappedError.setMessage("Mapped provider error");
        when(errorMappingCatalogService.mapProviderError(any())).thenReturn(mappedError);

        DirectOnlinePaymentRequestsController controller = new DirectOnlinePaymentRequestsController(
                camel,
                providersPayService,
                directOnlinePaymentMap,
                new ObjectMapper(),
                serviceMappingConfigService,
                errorMappingCatalogService,
                integrationLogService,
                gatewayWebServiceConfigService,
                banksCatalogService);

        ResponseEntity<?> response = controller.directOnlinePaymentRequests(buildRequest(new BigDecimal("25.00")));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(integrationLogService).logExternal(argThat((IntegrationLogRecord record) ->
                rawProviderError.equals(record.getResponsePayload())));
    }

    @Test
    void directOnlinePaymentRequestsReturnsMappedErrorWhenAmountExceedsConfiguredMaximum() {
        ProducerTemplate camel = mock(ProducerTemplate.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        DirectOnlinePaymentMap directOnlinePaymentMap = mock(DirectOnlinePaymentMap.class);
        ServiceMappingConfigService serviceMappingConfigService = mock(ServiceMappingConfigService.class);
        ErrorMappingCatalogService errorMappingCatalogService = mock(ErrorMappingCatalogService.class);
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        GatewayWebServiceConfigService gatewayWebServiceConfigService = mock(GatewayWebServiceConfigService.class);
        BanksCatalogService banksCatalogService = mock(BanksCatalogService.class);

        when(providersPayService.getProviderNameByCode(235689)).thenReturn("paysafe");
        when(gatewayWebServiceConfigService.isActive(235689, "direct-online-payment-requests")).thenReturn(true);
        when(banksCatalogService.findMinimum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("20.00")));
        when(banksCatalogService.findMaximum(235689, "0123")).thenReturn(Optional.of(new BigDecimal("50.00")));

        ErrorInfo mappedError = new ErrorInfo();
        mappedError.setHttp_code(422);
        mappedError.setCode("1005");
        mappedError.setCategory("ERROR_CATEGORY");
        mappedError.setMessage("El monto no cumple el maximo.");
        mappedError.setInner_details(java.util.List.of());
        when(errorMappingCatalogService.buildErrorByCurrentCode(1005L)).thenReturn(mappedError);

        DirectOnlinePaymentRequestsController controller = new DirectOnlinePaymentRequestsController(
                camel,
                providersPayService,
                directOnlinePaymentMap,
                new ObjectMapper(),
                serviceMappingConfigService,
                errorMappingCatalogService,
                integrationLogService,
                gatewayWebServiceConfigService,
                banksCatalogService);

        DirectOnlinePaymentRequest request = buildRequest(new BigDecimal("60.00"));

        ResponseEntity<?> response = controller.directOnlinePaymentRequests(request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getError().getCode()).isEqualTo("1005");
        assertThat(body.getError().getMessage()).isEqualTo("El monto no cumple el maximo.");
        verify(camel, never()).requestBodyAndHeaders(anyString(), any(), anyMap());
        verify(directOnlinePaymentMap, never()).mapProviderRequest(any(), any());
    }

    private DirectOnlinePaymentRequest buildRequest(BigDecimal amount) {
        DirectOnlinePaymentRequest request = new DirectOnlinePaymentRequest();
        request.setChain(1);
        request.setStore(148);
        request.setStore_name("FYBECA AMAZONAS");
        request.setPos(90);
        request.setChannel_POS("web");
        request.setPayment_provider_code(235689);
        request.setBank_id("0123");
        request.setMerchant_sales_id("Prueba1");
        request.setCountry_code("EC");

        DirectOnlinePaymentRequest.SalesAmount salesAmount = new DirectOnlinePaymentRequest.SalesAmount();
        salesAmount.setValue(amount);
        salesAmount.setCurrency_code("USD");
        request.setSales_amount(salesAmount);
        return request;
    }
}
