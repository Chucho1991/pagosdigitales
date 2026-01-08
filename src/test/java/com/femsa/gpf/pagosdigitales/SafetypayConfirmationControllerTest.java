package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.femsa.gpf.pagosdigitales.domain.service.SignatureService;

@SpringBootTest(properties = {
        "safetypay.confirmation.enabled=true",
        "safetypay.confirmation.providers.paysafe.apiKey=test-api-key",
        "safetypay.confirmation.providers.paysafe.secret=test-secret"
})
@AutoConfigureMockMvc
class SafetypayConfirmationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignatureService signatureService;

    @Test
    void confirmationOkAndIdempotent() throws Exception {
        Map<String, String> form = baseForm();
        form.put("Signature", sign(form));

        String response1 = doPost(form);
        assertThat(csvField(response1, 0)).isEqualTo("0");

        String response2 = doPost(form);
        assertThat(csvField(response2, 0)).isEqualTo("0");
    }

    @Test
    void confirmationInvalidApiKey() throws Exception {
        Map<String, String> form = baseForm();
        form.put("ApiKey", "bad-key");
        form.put("Signature", sign(form));

        String response = doPost(form);
        assertThat(csvField(response, 0)).isEqualTo("1");
    }

    @Test
    void confirmationInvalidSignature() throws Exception {
        Map<String, String> form = baseForm();
        form.put("Signature", "BAD");

        String response = doPost(form);
        assertThat(csvField(response, 0)).isEqualTo("2");
    }

    private String doPost(Map<String, String> form) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/safetypay/confirmation")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("payment_provider_code", form.get("payment_provider_code"))
                        .param("ApiKey", form.get("ApiKey"))
                        .param("RequestDateTime", form.get("RequestDateTime"))
                        .param("MerchantSalesID", form.get("MerchantSalesID"))
                        .param("ReferenceNo", form.get("ReferenceNo"))
                        .param("CreationDateTime", form.get("CreationDateTime"))
                        .param("Amount", form.get("Amount"))
                        .param("CurrencyID", form.get("CurrencyID"))
                        .param("PaymentReferenceNo", form.get("PaymentReferenceNo"))
                        .param("Status", form.get("Status"))
                        .param("Signature", form.get("Signature")))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private Map<String, String> baseForm() {
        return Map.of(
                "payment_provider_code", "235689",
                "ApiKey", "test-api-key",
                "RequestDateTime", "2020-10-20T12:27:27",
                "MerchantSalesID", "Prueba1",
                "ReferenceNo", "Ref1",
                "CreationDateTime", "2020-10-20T12:27:27",
                "Amount", "50.00",
                "CurrencyID", "USD",
                "PaymentReferenceNo", "606973",
                "Status", "102"
        );
    }

    private String sign(Map<String, String> form) {
        String base = form.get("RequestDateTime")
                + form.get("MerchantSalesID")
                + form.get("ReferenceNo")
                + form.get("CreationDateTime")
                + form.get("Amount")
                + form.get("CurrencyID")
                + form.get("PaymentReferenceNo")
                + form.get("Status")
                + "test-secret";
        return signatureService.sha256Hex(base);
    }

    private String csvField(String csvLine, int index) {
        String[] parts = csvLine.split(",", -1);
        return parts.length > index ? parts[index] : "";
    }
}
