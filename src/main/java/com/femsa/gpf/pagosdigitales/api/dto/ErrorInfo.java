package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;

import lombok.Data;

/**
 * Estructura de error estandar para respuestas de proveedores.
 */
@Data
public class ErrorInfo {

    private Integer http_code;
    private String code;
    private String category;
    private String message;
    private String information_link;
    private List<ErrorInnerDetail> inner_details;
}
