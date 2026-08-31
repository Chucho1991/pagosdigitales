package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.controller.PaymentsController;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperationActivity;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ErrorMappingCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService.WebServiceConfig;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService.RegisteredPayment;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class PaymentsControllerTest {

    private ProducerTemplate camel;
    private PaymentsMap paymentsMap;
    private PaymentRegistryService paymentRegistryService;
    private ProvidersPayService providersPayService;
    private GatewayWebServiceConfigService configService;
    private ServiceMappingConfigService mappingConfigService;
    private IntegrationLogService integrationLogService;
    private PaymentsController controller;

    @BeforeEach
    void setUp() {
        camel = mock(ProducerTemplate.class);
        paymentsMap = mock(PaymentsMap.class);
        paymentRegistryService = mock(PaymentRegistryService.class);
        providersPayService = mock(ProvidersPayService.class);
        configService = mock(GatewayWebServiceConfigService.class);
        mappingConfigService = mock(ServiceMappingConfigService.class);
        integrationLogService = mock(IntegrationLogService.class);

        when(providersPayService.getProviderNameByCode(300001)).thenReturn("jepfaster");
        when(configService.getActiveConfig(300001, "payments")).thenReturn(Optional.of(
                new WebServiceConfig(300001, "payments", true, "REST", "GET", "PARAMETROS", "INTERNO")));

        controller = new PaymentsController(
                camel,
                providersPayService,
                paymentsMap,
                new ObjectMapper(),
                mappingConfigService,
                mock(ErrorMappingCatalogService.class),
                integrationLogService,
                configService,
                paymentRegistryService);
    }

    @Test
    void getPaymentsReadsJepStatusFromRegistryWithoutCallingCamel() {
        when(paymentRegistryService.findPaymentStatus("8", 300001)).thenReturn(Optional.of(
                new RegisteredPayment(
                        LocalDateTime.of(2026, 2, 19, 9, 52, 3),
                        LocalDateTime.of(2026, 8, 19, 10, 36, 57),
                        "8", "8", "8", "8", null, null,
                        "102", "JEP_CONFIRMATION_OK")));

        ResponseEntity<?> result = controller.getPayments(request());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        PaymentsResponse response = (PaymentsResponse) result.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getChannel_POS()).isEqualTo("POS");
        assertThat(response.getPayment_operations()).hasSize(1);
        assertThat(response.getPayment_operations().get(0).getOperation_id()).isEqualTo("8");
        assertThat(response.getPayment_operations().get(0).getOperation_activities())
                .extracting(activity -> activity.getStatus_code())
                .containsExactly("101", "102");
        assertThat(response.getPayment_operations().get(0).getOperation_activities())
                .extracting(activity -> activity.getStatus_description())
                .containsExactly("Purchase Pending", "Purchase Complete");
        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300001".equals(record.getCodigoProvPago())
                        && Integer.valueOf(200).equals(record.getCpNumber1())
                        && response.equals(record.getResponsePayload())));
        verify(integrationLogService, never()).logExternal(any());
        verifyNoInteractions(camel, paymentsMap);
    }

    @Test
    void getPaymentsReturnsNotFoundWhenRegistryHasNoOperation() {
        when(paymentRegistryService.findPaymentStatus("8", 300001)).thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.getPayments(request());

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        ApiErrorResponse response = (ApiErrorResponse) result.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("PAYMENT_NOT_FOUND");
        verifyNoInteractions(camel, paymentsMap);
    }

    @Test
    void getPaymentsLogsDeunaInternalAndExternalConsumptions() {
        when(providersPayService.getProviderNameByCode(300002)).thenReturn("deuna");
        when(configService.getActiveConfig(300002, "payments")).thenReturn(Optional.of(
                new WebServiceConfig(300002, "payments", true, "REST", "GET", "JSON", "https://deuna/payments")));
        java.util.Map<String, Object> providerRequest = java.util.Map.of("idType", "0");
        java.util.Map<String, Object> providerResponse = java.util.Map.of(
                "status", "PENDING",
                "transactionId", "DEUNA-OP-1");
        PaymentsResponse mappedResponse = new PaymentsResponse();
        mappedResponse.setPayment_provider_code(300002);

        when(paymentsMap.mapProviderRequest(any(), eq("deuna"))).thenReturn(providerRequest);
        when(camel.requestBodyAndHeaders(eq("direct:payments"), eq(providerRequest), anyMap()))
                .thenReturn(providerResponse);
        when(mappingConfigService.getErrorPath(300002, "payments", "deuna")).thenReturn("error");
        when(paymentsMap.mapProviderResponse(any(), eq(providerResponse), eq("deuna"))).thenReturn(mappedResponse);

        PaymentsRequest request = request();
        request.setPayment_provider_code(300002);
        request.setOperation_id("DEUNA-OP-1");
        ResponseEntity<?> result = controller.getPayments(request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(integrationLogService).logExternal(argThat((IntegrationLogRecord record) ->
                "300002".equals(record.getCodigoProvPago())
                        && providerRequest.equals(record.getRequestPayload())
                        && providerResponse.equals(record.getResponsePayload())
                        && Integer.valueOf(200).equals(record.getCpNumber1())));
        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300002".equals(record.getCodigoProvPago())
                        && mappedResponse.equals(record.getResponsePayload())
                        && Integer.valueOf(200).equals(record.getCpNumber1())));
    }

    @Test
    void getPaymentsReturnsDeunaPaidHistoryFromRegistry() {
        when(providersPayService.getProviderNameByCode(300002)).thenReturn("deuna");
        when(configService.getActiveConfig(300002, "payments")).thenReturn(Optional.of(
                new WebServiceConfig(300002, "payments", true, "REST", "GET", "JSON", "https://deuna/payments")));
        java.util.Map<String, Object> providerRequest = java.util.Map.of("idType", "0");
        java.util.Map<String, Object> providerResponse = java.util.Map.of(
                "status", "PAID", "transactionId", "DEUNA-OP-1");

        PaymentOperationActivity externalActivity = new PaymentOperationActivity();
        externalActivity.setStatus_code("102");
        PaymentOperation operation = new PaymentOperation();
        operation.setOperation_id("DEUNA-OP-1");
        operation.setOperation_activities(List.of(externalActivity));
        PaymentsResponse mappedResponse = new PaymentsResponse();
        mappedResponse.setPayment_provider_code(300002);
        mappedResponse.setPayment_operations(List.of(operation));

        when(paymentsMap.mapProviderRequest(any(), eq("deuna"))).thenReturn(providerRequest);
        when(camel.requestBodyAndHeaders(eq("direct:payments"), eq(providerRequest), anyMap()))
                .thenReturn(providerResponse);
        when(mappingConfigService.getErrorPath(300002, "payments", "deuna")).thenReturn("error");
        when(paymentsMap.mapProviderResponse(any(), eq(providerResponse), eq("deuna"))).thenReturn(mappedResponse);
        when(paymentRegistryService.findPaymentStatus("DEUNA-OP-1", 300002)).thenReturn(Optional.of(
                new RegisteredPayment(
                        LocalDateTime.of(2026, 8, 26, 16, 40, 21),
                        LocalDateTime.of(2026, 8, 26, 16, 40, 39),
                        "SALE-1", "DEUNA-OP-1", "SALE-1", "REF-1",
                        new java.math.BigDecimal("3.93"), "USD", "102", "DEUNA_PAYMENT_STATUS_102")));

        PaymentsRequest request = request();
        request.setPayment_provider_code(300002);
        request.setOperation_id("DEUNA-OP-1");
        ResponseEntity<?> result = controller.getPayments(request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        PaymentsResponse response = (PaymentsResponse) result.getBody();
        assertThat(response.getPayment_operations().get(0).getOperation_activities())
                .extracting(PaymentOperationActivity::getStatus_code)
                .containsExactly("101", "102");
        verify(paymentRegistryService).synchronizeDeunaPaymentStatus(operation, 300002);
    }

    @Test
    void getPaymentsPreservesDeunaStatusWithoutInternalHomologation() {
        when(providersPayService.getProviderNameByCode(300002)).thenReturn("deuna");
        when(configService.getActiveConfig(300002, "payments")).thenReturn(Optional.of(
                new WebServiceConfig(300002, "payments", true, "REST", "POST", "JSON",
                        "https://deuna/payments")));
        java.util.Map<String, Object> providerRequest = java.util.Map.of(
                "idTransacionReference", "DEUNA-OP-1", "idType", "0");
        java.util.Map<String, Object> providerResponse = java.util.Map.of(
                "status", "REVERSED", "transactionId", "DEUNA-OP-1");

        PaymentOperationActivity externalActivity = new PaymentOperationActivity();
        externalActivity.setStatus_code("REVERSED");
        externalActivity.setStatus_description("REVERSED");
        PaymentOperation operation = new PaymentOperation();
        operation.setOperation_id("DEUNA-OP-1");
        operation.setOperation_activities(List.of(externalActivity));
        PaymentsResponse mappedResponse = new PaymentsResponse();
        mappedResponse.setPayment_provider_code(300002);
        mappedResponse.setPayment_operations(List.of(operation));

        when(paymentsMap.mapProviderRequest(any(), eq("deuna"))).thenReturn(providerRequest);
        when(camel.requestBodyAndHeaders(eq("direct:payments"), eq(providerRequest), anyMap()))
                .thenReturn(providerResponse);
        when(mappingConfigService.getErrorPath(300002, "payments", "deuna")).thenReturn("error");
        when(paymentsMap.mapProviderResponse(any(), eq(providerResponse), eq("deuna")))
                .thenReturn(mappedResponse);

        PaymentsRequest request = request();
        request.setPayment_provider_code(300002);
        request.setOperation_id("DEUNA-OP-1");

        ResponseEntity<?> result = controller.getPayments(request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        PaymentsResponse response = (PaymentsResponse) result.getBody();
        assertThat(response.getPayment_operations().get(0).getOperation_activities())
                .extracting(PaymentOperationActivity::getStatus_code)
                .containsExactly("REVERSED");
        verify(paymentRegistryService, never()).synchronizeDeunaPaymentStatus(operation, 300002);
        verify(paymentRegistryService, never()).findPaymentStatus("DEUNA-OP-1", 300002);
    }

    private PaymentsRequest request() {
        PaymentsRequest req = new PaymentsRequest();
        req.setChain(60);
        req.setStore(130);
        req.setStore_name("FYBECA AMAZONAS");
        req.setPos(1);
        req.setPayment_provider_code(300001);
        req.setOperation_id("8");
        req.setRequest_datetime("2026-01-08T16:25:55.357");
        return req;
    }
}
