package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para cachear y sincronizar comisiones por establecimiento emisor.
 */
@Log4j2
@Service
public class IssuerCommissionCatalogService {

    private static final String APPDFM_CONNECTION_NAME = "APPDFM";

    private static final String SELECT_ISSUER_COMMISSIONS = "SELECT "
            + "B.CODIGO_ESTABLECIMIENTO, "
            + "B.DESCRIPCION, "
            + "NVL(D.MONTO_MIN, 0) MONTO_MINIMO, "
            + "NVL(D.COMISION_FIJA, 0) COMISION_FIJA, "
            + "NVL(D.COMISION, 0) COMISION_VARIABLE "
            + "FROM TUKUNAFUNC.AD_TIPO_PAGO B, TUKUNAFUNC.AD_COMISION_TIPOPAGO D "
            + "WHERE B.CODIGO = D.CODIGO_TIPO_PAGO(+) "
            + "AND D.ACTIVO(+) = 'S'";

    private static final String MERGE_ISSUER_COMMISSION = "MERGE INTO FEMSA_EMISOR_COMISION T "
            + "USING (SELECT ? CODIGO_ESTABLECIMIENTO, ? NOMBRE, ? MONTO_MINIMO, "
            + "? COMISION_FIJA, ? COMISION_VARIABLE FROM DUAL) S "
            + "ON (T.CODIGO_ESTABLECIMIENTO = S.CODIGO_ESTABLECIMIENTO) "
            + "WHEN MATCHED THEN UPDATE SET "
            + "T.NOMBRE = S.NOMBRE, "
            + "T.MONTO_MINIMO = S.MONTO_MINIMO, "
            + "T.COMISION_FIJA = S.COMISION_FIJA, "
            + "T.COMISION_VARIABLE = S.COMISION_VARIABLE "
            + "WHEN NOT MATCHED THEN INSERT "
            + "(CODIGO_ESTABLECIMIENTO, NOMBRE, MONTO_MINIMO, COMISION_FIJA, COMISION_VARIABLE) "
            + "VALUES (S.CODIGO_ESTABLECIMIENTO, S.NOMBRE, S.MONTO_MINIMO, "
            + "S.COMISION_FIJA, S.COMISION_VARIABLE)";

    private static final String DELETE_ISSUER_COMMISSION = "DELETE FROM FEMSA_EMISOR_COMISION "
            + "WHERE CODIGO_ESTABLECIMIENTO = ?";

    private static final String SELECT_TARGET_ESTABLISHMENTS = "SELECT CODIGO_ESTABLECIMIENTO "
            + "FROM FEMSA_EMISOR_COMISION";

    private final DatabaseExecutor databaseExecutor;
    private final DatabaseConnectionRegistry connectionRegistry;
    private volatile Map<String, IssuerCommission> commissionsByEstablishment = Map.of();

    /**
     * Crea el servicio con acceso a las conexiones origen y destino.
     *
     * @param databaseExecutor ejecutor JDBC de la conexion primaria TUKUNAFUNC
     * @param connectionRegistry registro de conexiones JDBC nombradas
     */
    public IssuerCommissionCatalogService(DatabaseExecutor databaseExecutor,
            DatabaseConnectionRegistry connectionRegistry) {
        this.databaseExecutor = databaseExecutor;
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Inicializa la cache y sincroniza APPDFM al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de comisiones y sincroniza FEMSA_EMISOR_COMISION cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<String, IssuerCommission> loaded = Map.copyOf(loadFromDb());
            syncToAppdfm(loaded);
            this.commissionsByEstablishment = loaded;
            log.info("Cache AD_COMISION_TIPOPAGO actualizada. Comisiones cargadas: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache AD_COMISION_TIPOPAGO. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene la comision cacheada por codigo de establecimiento.
     *
     * @param establishmentCode codigo de establecimiento
     * @return comision cacheada si existe
     */
    public Optional<IssuerCommission> findByEstablishmentCode(String establishmentCode) {
        if (establishmentCode == null || establishmentCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(commissionsByEstablishment.get(establishmentCode.trim()));
    }

    private Map<String, IssuerCommission> loadFromDb() throws Exception {
        Map<String, IssuerCommission> loaded = new LinkedHashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_ISSUER_COMMISSIONS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    IssuerCommission commission = new IssuerCommission(
                            trimToEmpty(rs.getString("CODIGO_ESTABLECIMIENTO")),
                            trimToEmpty(rs.getString("DESCRIPCION")),
                            defaultZero(rs.getBigDecimal("MONTO_MINIMO")),
                            defaultZero(rs.getBigDecimal("COMISION_FIJA")),
                            defaultZero(rs.getBigDecimal("COMISION_VARIABLE")));
                    if (!commission.establishmentCode().isBlank()) {
                        loaded.put(commission.establishmentCode(), commission);
                    }
                }
            }
        });
        return loaded;
    }

    private void syncToAppdfm(Map<String, IssuerCommission> loaded) throws Exception {
        DataSource dataSource = connectionRegistry.getDataSource(APPDFM_CONNECTION_NAME);
        try (Connection connection = dataSource.getConnection()) {
            mergeCommissions(connection, loaded);
            deleteMissingCommissions(connection, loaded);
        }
    }

    private void mergeCommissions(Connection connection, Map<String, IssuerCommission> loaded) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(MERGE_ISSUER_COMMISSION)) {
            for (IssuerCommission commission : loaded.values()) {
                ps.setString(1, commission.establishmentCode());
                ps.setString(2, commission.name());
                ps.setBigDecimal(3, commission.minimumAmount());
                ps.setBigDecimal(4, commission.fixedCommission());
                ps.setBigDecimal(5, commission.variableCommission());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteMissingCommissions(Connection connection, Map<String, IssuerCommission> loaded) throws Exception {
        try (PreparedStatement select = connection.prepareStatement(SELECT_TARGET_ESTABLISHMENTS);
                ResultSet rs = select.executeQuery();
                PreparedStatement delete = connection.prepareStatement(DELETE_ISSUER_COMMISSION)) {
            while (rs.next()) {
                String establishmentCode = trimToEmpty(rs.getString("CODIGO_ESTABLECIMIENTO"));
                if (!establishmentCode.isBlank() && !loaded.containsKey(establishmentCode)) {
                    delete.setString(1, establishmentCode);
                    delete.addBatch();
                }
            }
            delete.executeBatch();
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Comision por establecimiento emisor.
     *
     * @param establishmentCode codigo de establecimiento
     * @param name nombre o descripcion del establecimiento
     * @param minimumAmount monto minimo
     * @param fixedCommission comision fija
     * @param variableCommission comision variable
     */
    public record IssuerCommission(
            String establishmentCode,
            String name,
            BigDecimal minimumAmount,
            BigDecimal fixedCommission,
            BigDecimal variableCommission) {
    }
}
