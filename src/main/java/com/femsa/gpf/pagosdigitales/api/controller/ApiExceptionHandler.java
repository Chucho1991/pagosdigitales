package com.femsa.gpf.pagosdigitales.api.controller;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInnerDetail;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;

/**
 * Manejador global para errores de validacion y payload invalido.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Maneja errores de validacion declarativa de DTOs.
     *
     * @param ex excepcion de validacion
     * @return respuesta de error normalizada
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
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
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Maneja payloads JSON malformados o cuerpos vacios.
     *
     * @param ex excepcion de lectura de mensaje HTTP
     * @return respuesta de error normalizada
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPayload(HttpMessageNotReadableException ex) {
        ErrorInfo error = ApiErrorUtils.invalidRequest("Payload invalido", null, null, null);
        ApiErrorResponse body = ApiErrorUtils.buildResponse(null, null, null, null, null, null, error);
        return ResponseEntity.badRequest().body(body);
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
