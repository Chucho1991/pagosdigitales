package com.femsa.gpf.pagosdigitales.api.dto;

import lombok.Data;

/**
 * Detalle interno de un error de validacion.
 */
@Data
public class ErrorInnerDetail {

    private String inner_code;
    private String field;
    private String field_value;
    private String field_message;
}
