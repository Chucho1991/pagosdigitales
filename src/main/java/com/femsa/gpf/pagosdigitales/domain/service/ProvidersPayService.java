package com.femsa.gpf.pagosdigitales.domain.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver codigos y nombres de proveedores de pago.
 */
@Log4j2
@Service
public class ProvidersPayService {

    private static final String SELECT_ACTIVE_WALLETS = "SELECT CODIGO, NOMBRE_BILLETERA_DIGITAL, "
            + "NVL(TIPO_BANCO, 'EXTERNO') AS TIPO_BANCO "
            + "FROM TUKUNAFUNC.AD_BILLETERAS_DIGITALES "
            + "WHERE ACTIVA = 'S'";

    private static final String BANK_TYPE_INTERNAL = "INTERNO";
    private static final String BANK_TYPE_EXTERNAL = "EXTERNO";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<String, Integer> providersByName = Map.of();
    private volatile Map<Integer, String> providersByCode = Map.of();
    private volatile Map<Integer, String> bankTypeByCode = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public ProvidersPayService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Inicializa la cache de proveedores al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de proveedores cada 6 horas.
     *
     * @return true si la cache fue actualizada; false si se conservo la anterior
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public boolean refreshCache() {
        try {
            Map<Integer, String[]> loaded = loadActiveProvidersFromDb();
            Map<String, Integer> refreshedByName = new LinkedHashMap<>();
            Map<Integer, String> refreshedByCode = new LinkedHashMap<>();
            Map<Integer, String> refreshedBankType = new LinkedHashMap<>();
            loaded.forEach((code, values) -> {
                String name = values[0];
                String tipo = values[1];
                refreshedByName.put(name, code);
                refreshedByCode.put(code, name);
                refreshedBankType.put(code, tipo);
            });
            this.providersByName = Map.copyOf(refreshedByName);
            this.providersByCode = Map.copyOf(refreshedByCode);
            this.bankTypeByCode = Map.copyOf(refreshedBankType);
            log.info("Cache de proveedores actualizada. Total activos: {}", refreshedByName.size());
            return true;
        } catch (Exception e) {
            log.error("No fue posible refrescar cache AD_BILLETERAS_DIGITALES. Se conserva cache anterior.", e);
            return false;
        }
    }

    /**
     * Busca el nombre del proveedor por su codigo.
     *
     * @param code codigo del proveedor
     * @return nombre del proveedor o "without-provider" si no existe
     */
    public String getProviderNameByCode(Integer code) {
        if (code == null) {
            return "without-provider";
        }
        return providersByCode.getOrDefault(code, "without-provider");
    }

    /**
     * Busca el codigo del proveedor por su nombre.
     *
     * @param name nombre del proveedor
     * @return codigo del proveedor o 0 si no existe
     */
    public Integer getProviderCodeByName(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        String normalized = normalizeProviderKey(name);
        return providersByName.getOrDefault(normalized, 0);
    }

    /**
     * Obtiene el mapa completo de proveedores configurados.
     *
     * @return mapa de proveedor a codigo
     */
    public Map<String, Integer> getAllProviders() {
        return providersByName;
    }

    /**
     * Obtiene el tipo de banco del proveedor (INTERNO o EXTERNO).
     *
     * @param code codigo del proveedor
     * @return tipo de banco; por defecto "EXTERNO"
     */
    public String getBankTypeByCode(Integer code) {
        if (code == null) {
            return BANK_TYPE_EXTERNAL;
        }
        return bankTypeByCode.getOrDefault(code, BANK_TYPE_EXTERNAL);
    }

    /**
     * Indica si el proveedor resuelve bancos de forma local (sin llamada externa).
     *
     * @param code codigo del proveedor
     * @return true si TIPO_BANCO es INTERNO
     */
    public boolean isInternalProvider(Integer code) {
        return BANK_TYPE_INTERNAL.equalsIgnoreCase(getBankTypeByCode(code));
    }

    private Map<Integer, String[]> loadActiveProvidersFromDb() throws Exception {
        Map<Integer, String[]> providers = new LinkedHashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_WALLETS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String providerName = normalizeProviderKey(rs.getString("NOMBRE_BILLETERA_DIGITAL"));
                    Integer providerCode = rs.getInt("CODIGO");
                    String tipoBanco = rs.getString("TIPO_BANCO");
                    if (tipoBanco == null || tipoBanco.isBlank()) {
                        tipoBanco = BANK_TYPE_EXTERNAL;
                    }
                    if (!providerName.isBlank()) {
                        providers.put(providerCode, new String[]{providerName, tipoBanco.trim().toUpperCase(Locale.ROOT)});
                    }
                }
            }
        });
        return providers;
    }

    private String normalizeProviderName(String providerName) {
        if (providerName == null) {
            return "";
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProviderKey(String providerName) {
        String normalized = normalizeProviderName(providerName).replaceAll("[^a-z0-9]", "");
        if (normalized.contains("paysafe") || normalized.contains("safetypay")) {
            return "paysafe";
        }
        if (normalized.contains("pichincha")) {
            return "pichincha";
        }
        if (normalized.contains("jep") || normalized.contains("jepfaster")) {
            return "jepfaster";
        }
        if (normalized.contains("deuna")) {
            return "deuna";
        }
        return normalized;
    }

}
