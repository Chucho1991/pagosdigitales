package com.femsa.gpf.pagosdigitales.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de solicitud para notificaciones de pago de JEPFaster (webhook).
 *
 * <p>Cooperativa JEP envia este payload cuando un pago QR se completa exitosamente.</p>
 */
@Data
public class JepConfirmationRequest {

    /**
     * Id de la transaccion generada al crear el QR.
     */
    @NotBlank(message = "idtransaccion requerido")
    private String idtransaccion;

    /**
     * Estado del pago; unicamente se espera "PAGADO".
     */
    @NotBlank(message = "estado requerido")
    private String estado;

    /**
     * Mensaje del estado de la transaccion.
     */
    @NotBlank(message = "mensaje requerido")
    private String mensaje;

    /**
     * Identificador de sesion opcional, enviado al momento de generar el QR.
     */
    private String identificadorsesion;

    /**
     * Numero de comprobante o documento generado al realizar el pago.
     */
    @NotBlank(message = "nummensaje requerido")
    private String nummensaje;

    /**
     * Parametro de control de errores; siempre debera tener valor "0" en pagos exitosos.
     */
    @NotBlank(message = "error requerido")
    private String error;
}
