package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.application.ports.out.ProviderTransactionSequencePort;

/**
 * Genera identificadores de proveedor mediante secuencias Oracle.
 */
@Component
public class OracleProviderTransactionSequenceAdapter implements ProviderTransactionSequencePort {

    private static final String SELECT_JEP_SEQUENCE =
            "SELECT TUKUNAFUNC.SEQ_JEP_MERCHANT_SALES.NEXTVAL FROM DUAL";
    private static final String SELECT_DEUNA_SEQUENCE =
            "SELECT TUKUNAFUNC.SEQ_DEUNA_MERCHANT_SALES.NEXTVAL FROM DUAL";

    private final DatabaseExecutor databaseExecutor;

    /**
     * Crea el adaptador con acceso a la base de datos principal.
     *
     * @param databaseExecutor ejecutor de conexiones JDBC
     */
    public OracleProviderTransactionSequenceAdapter(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    @Override
    public BigDecimal nextJepTransactionId() {
        return nextValue(SELECT_JEP_SEQUENCE, "JEPFaster");
    }

    @Override
    public BigDecimal nextDeunaTransactionId() {
        return nextValue(SELECT_DEUNA_SEQUENCE, "DEUNA");
    }

    private BigDecimal nextValue(String sql, String providerName) {
        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql);
                        ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException(
                                "La secuencia de " + providerName + " no devolvio un valor");
                    }
                    return resultSet.getBigDecimal(1);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No fue posible generar el secuencial para " + providerName, e);
        }
    }
}
