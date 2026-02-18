package com.femsa.gpf.pagosdigitales.domain.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver codigos y nombres de proveedores de pago.
 */
@Log4j2
@Service
public class ProvidersPayService {

    private static final String SELECT_ACTIVE_WALLETS = "SELECT CODIGO, NOMBRE_BILLETERA_DIGITAL "
            + "FROM TUKUNAFUNC.AD_BILLETERAS_DIGITALES "
            + "WHERE ACTIVA = 'S'";

    private final String dbUrl;
    private final Properties connectionProperties;

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param dbUrl URL JDBC
     * @param dbUsername usuario BD
     * @param dbPassword password BD
     */
    public ProvidersPayService(@Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.dbUrl = dbUrl;
        this.connectionProperties = buildConnectionProperties(dbUsername, dbPassword);
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
        return loadActiveProviders().entrySet().stream()
                .filter(entry -> code.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("without-provider");
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
        return loadActiveProviders().entrySet().stream()
                .filter(entry -> normalized.equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }

    /**
     * Obtiene el mapa completo de proveedores configurados.
     *
     * @return mapa de proveedor a codigo
     */
    public Map<String, Integer> getAllProviders() {
        return Map.copyOf(loadActiveProviders());
    }

    private Map<String, Integer> loadActiveProviders() {
        Map<String, Integer> providers = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_WALLETS);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String providerName = normalizeProviderKey(rs.getString("NOMBRE_BILLETERA_DIGITAL"));
                Integer providerCode = rs.getInt("CODIGO");
                if (!providerName.isBlank()) {
                    providers.put(providerName, providerCode);
                }
            }
            log.debug("Proveedores activos cargados desde AD_BILLETERAS_DIGITALES: {}", providers);
            return providers;
        } catch (Exception e) {
            log.error("No fue posible cargar AD_BILLETERAS_DIGITALES para resolver proveedores", e);
            return Map.of();
        }
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
        return normalized;
    }

    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        return props;
    }

}
