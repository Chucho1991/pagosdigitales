package com.femsa.gpf.pagosdigitales.api.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Item de comision emisora configurada para un establecimiento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssuerCommissionItem {

    private String codigo_establecimiento;
    private String nombre;
    private BigDecimal monto_minimo;
    private BigDecimal comision_fija;
    private BigDecimal comision_variable;
}
