package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;
import com.femsa.gpf.pagosdigitales.application.mapper.PaymentsMap;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class PaymentsMapTest {

    private ServiceMappingConfigService mappingService;
    private GatewayWebServiceDefinitionService definitionService;
    private PaymentsMap paymentsMap;

    @BeforeEach
    void setUp() {
        mappingService = mock(ServiceMappingConfigService.class);
        definitionService = mock(GatewayWebServiceDefinitionService.class);
        paymentsMap = new PaymentsMap(new ObjectMapper(), mappingService, definitionService);
    }

    @Test
    void mapProviderRequestBuildsDeunaJsonUsingPaymentsConfiguration() {
        when(mappingService.getRequestBodyMappings(300002, "payments", "deuna"))
                .thenReturn(Map.of("idTransacionReference", "operation_id"));
        when(mappingService.getRequestBodyDataTypes(300002, "payments", "deuna"))
                .thenReturn(Map.of("idTransacionReference", "STRING"));
        when(definitionService.getDefaults(
                org.mockito.ArgumentMatchers.eq(300002),
                org.mockito.ArgumentMatchers.eq("payments"),
                anyMap())).thenReturn(Map.of("idType", "0"));

        Map<String, Object> body = paymentsMap.mapProviderRequest(request(), "deuna");

        assertThat(body).containsExactly(
                Map.entry("idTransacionReference", "b392c0c5-ee17-49ae-b9cb-e96de453ad5d"),
                Map.entry("idType", "0"));
    }

    @Test
    void mapProviderResponseNormalizesFlatDeunaResponseAsSinglePaymentOperation() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("requestId", "transactionId");
        mappings.put("responseDatetime", "date");
        mappings.put("paymentOperation.creation_datetime", "date");
        mappings.put("paymentOperation.operation_id", "transactionId");
        mappings.put("paymentOperation.merchant_sales_id", "internalTransactionReference");
        mappings.put("paymentOperation.merchant_order_id", "internalTransactionReference");
        mappings.put("paymentOperation.payment_amount.value", "amount");
        mappings.put("paymentOperation.payment_amount.currency_code", "currency");
        mappings.put("paymentOperation.shopper_amount.value", "amount");
        mappings.put("paymentOperation.shopper_amount.currency_code", "currency");
        mappings.put("paymentOperation.additional_info.description", "description");
        mappings.put("paymentOperation.additional_info.branch_id", "branchId");
        mappings.put("paymentOperation.additional_info.pos_id", "posId");
        mappings.put("paymentOperation.additional_info.orderer_name", "ordererName");
        mappings.put("paymentOperation.additional_info.orderer_identification", "ordererIdentification");
        mappings.put("paymentOperation.payment_reference_number", "transferNumber");
        mappings.put("paymentOperationActivity.creation_datetime", "date");
        mappings.put("paymentOperationActivity.status_code", "status");
        mappings.put("paymentOperationActivity.status_description", "status");
        when(mappingService.getResponseBodyMappings(300002, "payments", "deuna"))
                .thenReturn(mappings);

        String raw = """
                {
                  "status": "APPROVED",
                  "internalTransactionReference": "04-0000007",
                  "amount": 0.02,
                  "transactionId": "b392c0c5-ee17-49ae-b9cb-e96de453ad5d",
                  "transferNumber": "459637351026",
                  "date": "6/15/2024, 11:37:57 AM",
                  "currency": "USD",
                  "description": "detail transaction",
                  "branchId": "4073390",
                  "posId": "4073391",
                  "ordererName": "CLIENTE DEUNA",
                  "ordererIdentification": "1600539421"
                }
                """;

        var response = paymentsMap.mapProviderResponse(request(), raw, "deuna");

        assertThat(response.getRequest_id()).isEqualTo("b392c0c5-ee17-49ae-b9cb-e96de453ad5d");
        assertThat(response.getPayment_operations()).hasSize(1);
        var operation = response.getPayment_operations().get(0);
        assertThat(operation.getOperation_id()).isEqualTo("b392c0c5-ee17-49ae-b9cb-e96de453ad5d");
        assertThat(operation.getPayment_amount().getValue()).isEqualByComparingTo("0.02");
        assertThat(operation.getPayment_amount().getCurrency_code()).isEqualTo("USD");
        assertThat(operation.getAdditional_info()).isInstanceOf(Map.class);
        Map<?, ?> additionalInfo = (Map<?, ?>) operation.getAdditional_info();
        assertThat(additionalInfo.get("description")).isEqualTo("detail transaction");
        assertThat(additionalInfo.get("branch_id")).isEqualTo("4073390");
        assertThat(additionalInfo.get("pos_id")).isEqualTo("4073391");
        assertThat(additionalInfo.get("orderer_name")).isEqualTo("CLIENTE DEUNA");
        assertThat(additionalInfo.get("orderer_identification")).isEqualTo("1600539421");
        assertThat(operation.getOperation_activities()).singleElement().satisfies(activity -> {
            assertThat(activity.getStatus_code()).isEqualTo("102");
            assertThat(activity.getStatus_description()).isEqualTo("Purchase Complete");
        });
    }

    private PaymentsRequest request() {
        PaymentsRequest request = new PaymentsRequest();
        request.setChain(60);
        request.setStore(4202373);
        request.setPos(1);
        request.setChannel_POS("POS");
        request.setPayment_provider_code(300002);
        request.setOperation_id("b392c0c5-ee17-49ae-b9cb-e96de453ad5d");
        request.setRequest_datetime("2026-01-08T16:25:55.357");
        return request;
    }
}
