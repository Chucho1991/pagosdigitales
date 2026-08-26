package com.femsa.gpf.pagosdigitales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.femsa.gpf.pagosdigitales.api.controller.JepConfirmationController;
import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

class JepConfirmationControllerTest {

    private PaymentRegistryService paymentRegistryService;
    private IntegrationLogService integrationLogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        integrationLogService = mock(IntegrationLogService.class);
        paymentRegistryService = mock(PaymentRegistryService.class);
        JepConfirmationController controller = new JepConfirmationController(
                integrationLogService,
                paymentRegistryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void notifyPaymentUpdatesPaymentAndReturnsOk() throws Exception {
        when(paymentRegistryService.updateFromJepConfirmation(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/jep/notifyPayment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"OK\"}", true));

        verify(paymentRegistryService).updateFromJepConfirmation(any(JepConfirmationRequest.class));
        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300001".equals(record.getCodigoProvPago())
                        && "OK".equals(record.getMensaje())
                        && record.getResponsePayload() instanceof java.util.Map<?, ?> response
                        && "OK".equals(response.get("status"))));
    }

    @Test
    void notifyPaymentRejectsUnexpectedStatus() throws Exception {
        mockMvc.perform(post("/api/v1/jep/notifyPayment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload().replace("PAGADO", "PENDIENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"status\":\"ERROR\",\"message\":\"Estado no esperado: PENDIENTE\"}", true));

        verify(paymentRegistryService, never()).updateFromJepConfirmation(any());
    }

    @Test
    void notifyPaymentRequiresDocumentFields() throws Exception {
        mockMvc.perform(post("/api/v1/jep/notifyPayment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idtransaccion\":\"JEP-1\",\"estado\":\"PAGADO\"}"))
                .andExpect(status().isBadRequest());

        verify(paymentRegistryService, never()).updateFromJepConfirmation(any());
    }

    private String validPayload() {
        return """
                {
                  "idtransaccion": "JEP-1",
                  "estado": "PAGADO",
                  "mensaje": "Pago realizado",
                  "identificadorsesion": "SESION-1",
                  "nummensaje": "COMPROBANTE-1",
                  "error": "0"
                }
                """;
    }
}
