package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionItem;

/**
 * Servicio de lectura para la tabla TRX3.FEMSA_EMISOR_COMISION.
 */
@Service
public class IssuerCommissionQueryService {

    private static final String APPDFM_CONNECTION_NAME = "APPDFM";

    private static final String SELECT_ISSUER_COMMISSIONS = "SELECT "
            + "CODIGO_ESTABLECIMIENTO, "
            + "NOMBRE, "
            + "MONTO_MINIMO, "
            + "COMISION_FIJA, "
            + "COMISION_VARIABLE "
            + "FROM TRX3.FEMSA_EMISOR_COMISION "
            + "ORDER BY CODIGO_ESTABLECIMIENTO";

    private static final String SELECT_ISSUER_COMMISSIONS_BY_ESTABLISHMENT = "SELECT "
            + "CODIGO_ESTABLECIMIENTO, "
            + "NOMBRE, "
            + "MONTO_MINIMO, "
            + "COMISION_FIJA, "
            + "COMISION_VARIABLE "
            + "FROM TRX3.FEMSA_EMISOR_COMISION "
            + "WHERE CODIGO_ESTABLECIMIENTO = ? "
            + "ORDER BY CODIGO_ESTABLECIMIENTO";

    private final DatabaseConnectionRegistry connectionRegistry;

    /**
     * Crea el servicio de consulta de comisiones emisoras.
     *
     * @param connectionRegistry registro de conexiones JDBC
     */
    public IssuerCommissionQueryService(DatabaseConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Consulta comisiones emisoras, opcionalmente por codigo de establecimiento.
     *
     * @param establishmentCode codigo de establecimiento opcional
     * @return comisiones encontradas en TRX3.FEMSA_EMISOR_COMISION
     * @throws Exception cuando ocurre un error JDBC
     */
    public List<IssuerCommissionItem> findIssuerCommissions(String establishmentCode) throws Exception {
        String normalizedCode = normalize(establishmentCode);
        DataSource dataSource = connectionRegistry.getDataSource(APPDFM_CONNECTION_NAME);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        normalizedCode == null ? SELECT_ISSUER_COMMISSIONS
                                : SELECT_ISSUER_COMMISSIONS_BY_ESTABLISHMENT)) {
            if (normalizedCode != null) {
                ps.setString(1, normalizedCode);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<IssuerCommissionItem> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new IssuerCommissionItem(
                            trimToNull(rs.getString("CODIGO_ESTABLECIMIENTO")),
                            trimToNull(rs.getString("NOMBRE")),
                            rs.getBigDecimal("MONTO_MINIMO"),
                            rs.getBigDecimal("COMISION_FIJA"),
                            rs.getBigDecimal("COMISION_VARIABLE")));
                }
                return results;
            }
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
