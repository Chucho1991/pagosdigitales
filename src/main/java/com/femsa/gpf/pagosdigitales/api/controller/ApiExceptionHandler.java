package com.femsa.gpf.pagosdigitales.api.controller;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.DeunaConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInnerDetail;
import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;

/**
 * Manejador global para errores de validacion y payload invalido.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final int BAD_REQUEST = 400;

    private final IntegrationLogService integrationLogService;

    /**
     * Crea el manejador con el servicio de auditoria.
     *
     * @param integrationLogService servicio de persistencia de logs internos
     */
    public ApiExceptionHandler(IntegrationLogService integrationLogService) {
        this.integrationLogService = integrationLogService;
    }

    /**
     * Maneja errores de validacion declarativa de DTOs.
     *
     * @param ex excepcion de validacion
     * @param request solicitud HTTP recibida
     * @return respuesta de error normalizada
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Object target = ex.getBindingResult().getTarget();
        List<ErrorInnerDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();

        ErrorInfo error = new ErrorInfo();
        error.setHttp_code(400);
        error.setCode("INVALID_REQUEST");
        error.setCategory("INVALID_REQUEST_ERROR");
        error.setMessage("Request invalido");
        error.setInformation_link(null);
        error.setInner_details(details);

        ApiErrorResponse body = ApiErrorUtils.buildResponse(
                readInteger(target, "getChain"),
                readInteger(target, "getStore"),
                readString(target, "getStore_name"),
                readInteger(target, "getPos"),
                readString(target, "getChannel_POS"),
                readInteger(target, "getPayment_provider_code"),
                error);
        logInvalidInternalRequest(target, body, request, "INVALID_REQUEST");
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Maneja payloads JSON malformados o cuerpos vacios.
     *
     * @param ex excepcion de lectura de mensaje HTTP
     * @param request solicitud HTTP recibida
     * @return respuesta de error normalizada
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPayload(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ErrorInfo error = ApiErrorUtils.invalidRequest("Payload invalido", null, null, null);
        ApiErrorResponse body = ApiErrorUtils.buildResponse(null, null, null, null, null, null, error);
        logInvalidInternalRequest(null, body, request, "PAYLOAD_INVALIDO");
        return ResponseEntity.badRequest().body(body);
    }

    private void logInvalidInternalRequest(Object target, ApiErrorResponse response, HttpServletRequest request,
            String message) {
        Integer providerCode = resolveProviderCode(target, request.getRequestURI());
        integrationLogService.logInternal(IntegrationLogRecord.builder()
                .requestPayload(target)
                .responsePayload(response)
                .usuario("SYSTEM")
                .mensaje(message)
                .origen("WS_INTERNO")
                .pais(readString(target, "getCountry_code"))
                .canal(readString(target, "getChannel_POS"))
                .codigoProvPago(providerCode == null ? null : providerCode.toString())
                .nombreFarmacia(readString(target, "getStore_name"))
                .folio(resolveFolio(target))
                .farmacia(readInteger(target, "getStore"))
                .cadena(readInteger(target, "getChain"))
                .pos(readInteger(target, "getPos"))
                .url(request.getRequestURI())
                .metodo(request.getMethod())
                .cpVar1(resolveServiceKey(request.getRequestURI()))
                .cpVar2(message)
                .cpNumber1(BAD_REQUEST)
                .build());
    }

    private Integer resolveProviderCode(Object target, String requestUri) {
        Integer providerCode = readInteger(target, "getPayment_provider_code");
        if (providerCode != null) {
            return providerCode;
        }
        if (target instanceof JepConfirmationRequest || containsPath(requestUri, "/jep/")) {
            return 300001;
        }
        if (target instanceof DeunaConfirmationRequest || containsPath(requestUri, "/deuna/")) {
            return 300002;
        }
        return null;
    }

    private boolean containsPath(String requestUri, String path) {
        return requestUri != null && requestUri.contains(path);
    }

    private String resolveFolio(Object target) {
        String folio = readString(target, "getMerchant_sales_id");
        if (folio != null && !folio.isBlank()) {
            return folio;
        }
        folio = readString(target, "getOperation_id");
        if (folio != null && !folio.isBlank()) {
            return folio;
        }
        folio = readString(target, "getIdtransaccion");
        return folio == null ? readString(target, "getIdTransaction") : folio;
    }

    private String resolveServiceKey(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }
        int lastSeparator = requestUri.lastIndexOf('/');
        return lastSeparator < 0 ? requestUri : requestUri.substring(lastSeparator + 1);
    }

    private ErrorInnerDetail toDetail(FieldError fieldError) {
        ErrorInnerDetail detail = new ErrorInnerDetail();
        detail.setInner_code(null);
        detail.setField(fieldError.getField());
        detail.setField_value(fieldError.getRejectedValue() == null ? null : String.valueOf(fieldError.getRejectedValue()));
        detail.setField_message(fieldError.getDefaultMessage());
        return detail;
    }

    private Integer readInteger(Object target, String getterName) {
        Object value = readValue(target, getterName);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        return null;
    }

    private String readString(Object target, String getterName) {
        Object value = readValue(target, getterName);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    private Object readValue(Object target, String getterName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(getterName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
