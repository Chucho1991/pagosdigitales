package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Consulta el punto de venta externo configurado para una ubicacion y POS.
 */
@Service
public class PointOfSaleConfigService {

    private static final String SELECT_POINT_OF_SALE = "SELECT POINT_OF_SALE "
            + "FROM TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA "
            + "WHERE CODIGO_BILLETERA = ? "
            + "AND CODIGO_CADENA = ? "
            + "AND CODIGO_LOCAL = ? "
            + "AND CODIGO_POS = ? "
            + "AND ACTIVO = 'S'";

    private final DatabaseExecutor databaseExecutor;

    /**
     * Crea el servicio con acceso a la base de datos principal.
     *
     * @param databaseExecutor ejecutor de conexiones JDBC
     */
    public PointOfSaleConfigService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Busca el punto de venta por proveedor, cadena, local y POS.
     *
     * @param providerCode codigo del proveedor de pago
     * @param chain codigo de cadena
     * @param store codigo del local
     * @param pos numero de POS
     * @return punto de venta configurado, si existe y esta activo
     * @throws IllegalStateException cuando no es posible consultar la tabla
     */
    public Optional<String> findPointOfSale(Integer providerCode, Integer chain, Integer store, Integer pos) {
        if (providerCode == null || chain == null || store == null || pos == null) {
            return Optional.empty();
        }

        try {
            return databaseExecutor.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_POINT_OF_SALE)) {
                    statement.setInt(1, providerCode);
                    statement.setInt(2, chain);
                    statement.setInt(3, store);
                    statement.setInt(4, pos);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        String pointOfSale = resultSet.getString("POINT_OF_SALE");
                        return pointOfSale == null || pointOfSale.isBlank()
                                ? Optional.empty()
                                : Optional.of(pointOfSale.trim());
                    }
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible consultar la configuracion del punto de venta", e);
        }
    }
}
