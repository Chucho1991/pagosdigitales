package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de consulta de comisiones emisoras.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssuerCommissionResponse {

    private String codigo_establecimiento;
    private Integer total;
    private List<IssuerCommissionItem> commissions;
}
