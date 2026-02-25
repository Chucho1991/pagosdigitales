package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.api.dto.MerchantEvent;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentsRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void paymentsRequestRequiresProviderAndOperationId() {
        PaymentsRequest request = new PaymentsRequest();
        request.setPayment_provider_code(null);
        request.setOperation_id(" ");

        Set<ConstraintViolation<PaymentsRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("payment_provider_code", "operation_id");
    }

    @Test
    void merchantEventsRequestValidatesNestedMerchantSalesId() {
        MerchantEvent event = new MerchantEvent();
        event.setMerchant_sales_id(" ");

        MerchantEventsRequest request = new MerchantEventsRequest();
        request.setPayment_provider_code(235689);
        request.setMerchant_events(List.of(event));

        Set<ConstraintViolation<MerchantEventsRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("merchant_events[0].merchant_sales_id");
    }
}
