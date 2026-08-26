package com.femsa.gpf.pagosdigitales;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.controller.ApiExceptionHandler;
import com.femsa.gpf.pagosdigitales.api.dto.DeunaConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;

class ApiExceptionHandlerTest {

    private IntegrationLogService integrationLogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        integrationLogService = mock(IntegrationLogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new ApiExceptionHandler(integrationLogService))
                .build();
    }

    @Test
    void validationFailureLogsJepInternalConsumption() throws Exception {
        mockMvc.perform(post("/api/v1/jep/notifyPayment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idtransaccion\":\"JEP-1\",\"estado\":\"PAGADO\"}"))
                .andExpect(status().isBadRequest());

        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300001".equals(record.getCodigoProvPago())
                        && "JEP-1".equals(record.getFolio())
                        && "/api/v1/jep/notifyPayment".equals(record.getUrl())
                        && Integer.valueOf(400).equals(record.getCpNumber1())
                        && record.getResponsePayload() != null));
    }

    @Test
    void malformedPayloadLogsDeunaInternalConsumption() throws Exception {
        mockMvc.perform(post("/api/v1/deuna/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verify(integrationLogService).logInternal(argThat((IntegrationLogRecord record) ->
                "300002".equals(record.getCodigoProvPago())
                        && "/api/v1/deuna/confirmation".equals(record.getUrl())
                        && "PAYLOAD_INVALIDO".equals(record.getMensaje())
                        && Integer.valueOf(400).equals(record.getCpNumber1())));
    }

    @RestController
    static class ValidationController {

        @PostMapping("/api/v1/jep/notifyPayment")
        void jep(@Valid @RequestBody JepConfirmationRequest request) {
        }

        @PostMapping("/api/v1/deuna/confirmation")
        void deuna(@Valid @RequestBody DeunaConfirmationRequest request) {
        }
    }
}
