package com.femsa.gpf.pagosdigitales.api.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.femsa.gpf.pagosdigitales.api.dto.ApiErrorResponse;
import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionItem;
import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionResponse;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.IssuerCommissionQueryService;
import com.femsa.gpf.pagosdigitales.infrastructure.util.ApiErrorUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Controlador REST para consultar comisiones emisoras.
 */
@Log4j2
@RestController
@RequestMapping("/api/v1")
public class IssuerCommissionController {

    private final IssuerCommissionQueryService issuerCommissionQueryService;

    /**
     * Crea el controlador de consulta de comisiones emisoras.
     *
     * @param issuerCommissionQueryService servicio de lectura de comisiones emisoras
     */
    public IssuerCommissionController(IssuerCommissionQueryService issuerCommissionQueryService) {
        this.issuerCommissionQueryService = issuerCommissionQueryService;
    }

    /**
     * Consulta la informacion de TRX3.FEMSA_EMISOR_COMISION.
     *
     * @param codigoEstablecimiento filtro opcional por codigo de establecimiento
     * @return respuesta con las comisiones encontradas o error normalizado
     */
    @GetMapping(value = "/issuer-commissions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIssuerCommissions(
            @RequestParam(name = "codigo_establecimiento", required = false) String codigoEstablecimiento) {
        String normalizedCode = normalize(codigoEstablecimiento);
        if (codigoEstablecimiento != null && normalizedCode == null) {
            ApiErrorResponse errorBody = ApiErrorUtils.buildResponse(null, null, null, null, null, null,
                    ApiErrorUtils.invalidRequest("codigo_establecimiento no puede estar vacio",
                            "codigo_establecimiento", codigoEstablecimiento, "Debe informar un valor no vacio"));
            return ResponseEntity.badRequest().body(errorBody);
        }

        try {
            List<IssuerCommissionItem> commissions =
                    issuerCommissionQueryService.findIssuerCommissions(normalizedCode);
            return ResponseEntity.ok(new IssuerCommissionResponse(normalizedCode, commissions.size(), commissions));
        } catch (Exception e) {
            log.error("Error consultando TRX3.FEMSA_EMISOR_COMISION", e);
            ApiErrorResponse errorBody = ApiErrorUtils.buildResponse(null, null, null, null, null, null,
                    ApiErrorUtils.genericError(500, "Internal error"));
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
