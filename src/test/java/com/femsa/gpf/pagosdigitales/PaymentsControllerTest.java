package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.controller.PaymentsController;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsResponse;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
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
    private PaymentsController controller;

    @BeforeEach
    void setUp() {
        camel = mock(ProducerTemplate.class);
        paymentsMap = mock(PaymentsMap.class);
        paymentRegistryService = mock(PaymentRegistryService.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        GatewayWebServiceConfigService configService = mock(GatewayWebServiceConfigService.class);

        when(providersPayService.getProviderNameByCode(300001)).thenReturn("jepfaster");
        when(configService.getActiveConfig(300001, "payments")).thenReturn(Optional.of(
                new WebServiceConfig(300001, "payments", true, "REST", "GET", "PARAMETROS", "INTERNO")));

        controller = new PaymentsController(
                camel,
                providersPayService,
                paymentsMap,
                new ObjectMapper(),
                mock(ServiceMappingConfigService.class),
                mock(ErrorMappingCatalogService.class),
                mock(IntegrationLogService.class),
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
                        "PAGADO", "JEP_CONFIRMATION_OK")));

        ResponseEntity<?> result = controller.getPayments(request());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        PaymentsResponse response = (PaymentsResponse) result.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getChannel_POS()).isEqualTo("POS");
        assertThat(response.getPayment_operations()).hasSize(1);
        assertThat(response.getPayment_operations().get(0).getOperation_id()).isEqualTo("8");
        assertThat(response.getPayment_operations().get(0).getOperation_activities())
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.getStatus_code()).isEqualTo("PAGADO");
                    assertThat(activity.getStatus_description()).isEqualTo("JEP_CONFIRMATION_OK");
                });
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
