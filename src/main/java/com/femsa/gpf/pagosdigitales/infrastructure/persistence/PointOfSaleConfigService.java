package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Mantiene en cache los puntos de venta externos configurados por ubicacion y POS.
 */
@Log4j2
@Service
public class PointOfSaleConfigService {

    private static final String SELECT_POINT_OF_SALES = "SELECT CODIGO_BILLETERA, CODIGO_CADENA, "
            + "CODIGO_LOCAL, CODIGO_POS, POINT_OF_SALE "
            + "FROM TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA "
            + "WHERE ACTIVO = 'S' "
            + "ORDER BY CODIGO_BILLETERA, CODIGO_CADENA, CODIGO_LOCAL, CODIGO_POS";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<PointOfSaleKey, String> pointOfSales = Map.of();

    /**
     * Crea el servicio con acceso a la base de datos principal.
     *
     * @param databaseExecutor ejecutor de conexiones JDBC
     */
    public PointOfSaleConfigService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Inicializa la cache de puntos de venta al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de puntos de venta cada seis horas.
     *
     * @return true si la cache fue actualizada
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public boolean refreshCache() {
        try {
            Map<PointOfSaleKey, String> loaded = loadPointOfSalesFromDb();
            this.pointOfSales = Map.copyOf(loaded);
            log.info("Cache IN_PASARELA_PUNTO_VENTA actualizada. Puntos de venta cargados: {}", loaded.size());
            return true;
        } catch (Exception e) {
            log.error("No fue posible refrescar cache IN_PASARELA_PUNTO_VENTA. Se conserva cache anterior.", e);
            return false;
        }
    }

    /**
     * Busca el punto de venta por proveedor, cadena, local y POS.
     *
     * @param providerCode codigo del proveedor de pago
     * @param chain codigo de cadena
     * @param store codigo del local
     * @param pos numero de POS
     * @return punto de venta configurado, si existe y esta activo
     */
    public Optional<String> findPointOfSale(Integer providerCode, Integer chain, Integer store, Integer pos) {
        if (providerCode == null || chain == null || store == null || pos == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(pointOfSales.get(new PointOfSaleKey(providerCode, chain, store, pos)));
    }

    private Map<PointOfSaleKey, String> loadPointOfSalesFromDb() throws Exception {
        Map<PointOfSaleKey, String> loaded = new LinkedHashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_POINT_OF_SALES);
                    ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer providerCode = getInteger(resultSet, "CODIGO_BILLETERA");
                    Integer chain = getInteger(resultSet, "CODIGO_CADENA");
                    Integer store = getInteger(resultSet, "CODIGO_LOCAL");
                    Integer pos = getInteger(resultSet, "CODIGO_POS");
                    String pointOfSale = resultSet.getString("POINT_OF_SALE");
                    if (providerCode != null && chain != null && store != null && pos != null
                            && pointOfSale != null && !pointOfSale.isBlank()) {
                        loaded.put(
                                new PointOfSaleKey(providerCode, chain, store, pos),
                                pointOfSale.trim());
                    }
                }
            }
        });
        return loaded;
    }

    private Integer getInteger(ResultSet resultSet, String columnName) throws Exception {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private record PointOfSaleKey(Integer providerCode, Integer chain, Integer store, Integer pos) {
    }
}
