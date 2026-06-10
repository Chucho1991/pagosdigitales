package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.femsa.gpf.pagosdigitales.api.controller.IssuerCommissionController;
import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionItem;
import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.IssuerCommissionQueryService;

class IssuerCommissionControllerTest {

    @Test
    void getIssuerCommissionsReturnsFilteredCommissions() throws Exception {
        IssuerCommissionQueryService queryService = mock(IssuerCommissionQueryService.class);
        IssuerCommissionItem item = new IssuerCommissionItem(
                "001", "Banco Uno", new BigDecimal("1.25"), new BigDecimal("0.50"), new BigDecimal("0.10"));
        when(queryService.findIssuerCommissions("001")).thenReturn(List.of(item));

        IssuerCommissionController controller = new IssuerCommissionController(queryService);

        ResponseEntity<?> response = controller.getIssuerCommissions(" 001 ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(IssuerCommissionResponse.class);
        IssuerCommissionResponse body = (IssuerCommissionResponse) response.getBody();
        assertThat(body.getCodigo_establecimiento()).isEqualTo("001");
        assertThat(body.getTotal()).isEqualTo(1);
        assertThat(body.getCommissions()).containsExactly(item);
        verify(queryService).findIssuerCommissions("001");
    }

    @Test
    void getIssuerCommissionsRejectsBlankFilter() {
        IssuerCommissionQueryService queryService = mock(IssuerCommissionQueryService.class);
        IssuerCommissionController controller = new IssuerCommissionController(queryService);

        ResponseEntity<?> response = controller.getIssuerCommissions(" ");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertThat(body.getError().getCode()).isEqualTo("INVALID_REQUEST");
        assertThat(body.getError().getInner_details().get(0).getField()).isEqualTo("codigo_establecimiento");
    }
}
