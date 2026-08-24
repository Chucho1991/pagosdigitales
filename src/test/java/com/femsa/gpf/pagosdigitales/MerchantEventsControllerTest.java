package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.femsa.gpf.pagosdigitales.api.controller.MerchantEventsController;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEvent;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsResponse;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

class MerchantEventsControllerTest {

    @Test
    void merchantEventsDelegatesExistingOperationToUpsert() {
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        PaymentRegistryService paymentRegistryService = mock(PaymentRegistryService.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        MerchantEventsRequest request = buildRequest();

        when(providersPayService.getProviderNameByCode(300001)).thenReturn("jep");

        MerchantEventsController controller = new MerchantEventsController(
                integrationLogService, paymentRegistryService, providersPayService);

        ResponseEntity<?> response = controller.merchantEvents(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(MerchantEventsResponse.class);
        verify(paymentRegistryService).registerMerchantEvents(request, 0);
    }

    @Test
    void merchantEventsRejectsOperationAssignedToAnotherSale() {
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        PaymentRegistryService paymentRegistryService = mock(PaymentRegistryService.class);
        ProvidersPayService providersPayService = mock(ProvidersPayService.class);
        MerchantEventsRequest request = buildRequest();

        when(providersPayService.getProviderNameByCode(300001)).thenReturn("jep");
        when(paymentRegistryService.validateOperationIdOwnership(request))
                .thenReturn("operation_id OP-1 pertenece a otra venta");

        MerchantEventsController controller = new MerchantEventsController(
                integrationLogService, paymentRegistryService, providersPayService);

        ResponseEntity<?> response = controller.merchantEvents(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        verify(paymentRegistryService, never()).registerMerchantEvents(request, 0);
    }

    private MerchantEventsRequest buildRequest() {
        MerchantEvent event = new MerchantEvent();
        event.setCreation_datetime("2026-08-24T12:00:00");
        event.setOperation_id("OP-1");
        event.setMerchant_sales_id("SALE-1");

        MerchantEventsRequest request = new MerchantEventsRequest();
        request.setChain(1);
        request.setStore(148);
        request.setStore_name("FYBECA");
        request.setPos(90);
        request.setChannel_POS("web");
        request.setPayment_provider_code(300001);
        request.setMerchant_events(List.of(event));
        return request;
    }
}
