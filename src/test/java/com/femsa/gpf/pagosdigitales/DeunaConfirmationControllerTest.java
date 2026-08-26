package com.femsa.gpf.pagosdigitales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.femsa.gpf.pagosdigitales.api.controller.DeunaConfirmationController;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

class DeunaConfirmationControllerTest {

    private IntegrationLogService integrationLogService;
    private PaymentRegistryService paymentRegistryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        integrationLogService = mock(IntegrationLogService.class);
        paymentRegistryService = mock(PaymentRegistryService.class);
        DeunaConfirmationController controller = new DeunaConfirmationController(
                integrationLogService,
                paymentRegistryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void confirmationLogsInternalConsumptionWithDeunaProviderCode() throws Exception {
        when(paymentRegistryService.updateFromDeunaConfirmation(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/deuna/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS",
                                  "amount": 15.24,
                                  "idTransaction": "DEUNA-1",
                                  "transferNumber": "TR-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"estado\":\"OK\",\"mensaje\":\"Pago confirmado exitosamente\"}", true));

        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300002".equals(record.getCodigoProvPago())
                        && "DEUNA-1".equals(record.getFolio())
                        && "OK".equals(record.getMensaje())
                        && record.getResponsePayload() instanceof java.util.Map<?, ?> response
                        && "OK".equals(response.get("estado"))));
    }
}
