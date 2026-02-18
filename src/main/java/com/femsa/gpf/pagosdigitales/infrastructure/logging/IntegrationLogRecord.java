package com.femsa.gpf.pagosdigitales.infrastructure.logging;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Modelo comun para registrar consumos internos y externos en tablas de log.
 */
@Data
@Builder
public class IntegrationLogRecord {

    private Object requestPayload;
    private Object responsePayload;
    private String usuario;
    private String mensaje;
    private String origen;
    private String pais;
    private String canal;
    private String codigoProvPago;
    private String nombreFarmacia;
    private String folio;
    private Integer farmacia;
    private Integer cadena;
    private Integer pos;
    private String url;
    private String metodo;
    private String cpVar1;
    private String cpVar2;
    private String cpVar3;
    private Integer cpNumber1;
    private Integer cpNumber2;
    private Integer cpNumber3;
    private LocalDateTime cpDate1;
    private LocalDateTime cpDate2;
    private LocalDateTime cpDate3;
}
