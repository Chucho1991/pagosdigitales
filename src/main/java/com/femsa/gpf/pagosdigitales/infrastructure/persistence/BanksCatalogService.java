package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

/**
 * Servicio para consultar bancos permitidos por proveedor y cadena.
 */
@Log4j2
@Service
public class BanksCatalogService {

    private static final String SELECT_ACTIVE_BANKS = "SELECT CODIGO, CODIGO_BILLETERA_DIGITAL, "
            + "NVL(CADENA_FYB, 'N') CADENA_FYB, NVL(CADENA_SANA, 'N') CADENA_SANA, "
            + "NVL(CADENA_OKI, 'N') CADENA_OKI, NVL(CADENA_FR, 'N') CADENA_FR "
            + "FROM TUKUNAFUNC.AD_TIPO_PAGO "
            + "WHERE NVL(ACTIVO, 'N') = 'S'";

    private final String dbUrl;
    private final Properties connectionProperties;
    private final Integer codGeoFyb;
    private final Integer codGeoSana;
    private final Integer codGeoOki;
    private final Integer codGeoFr;
    private volatile Map<String, Set<String>> allowedBanksByProviderAndChain = Map.of();

    /**
     * Crea el servicio con configuracion de BD y codigos de cadena.
     *
     * @param dbUrl URL JDBC
     * @param dbUsername usuario BD
     * @param dbPassword password BD
     * @param codGeoFyb codigo de cadena FYB
     * @param codGeoSana codigo de cadena SANA
     * @param codGeoOki codigo de cadena OKI
     * @param codGeoFr codigo de cadena FR
     */
    public BanksCatalogService(@Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${cod_GEO_FYB}") Integer codGeoFyb,
            @Value("${cod_GEO_SANA}") Integer codGeoSana,
            @Value("${cod_GEO_OKI}") Integer codGeoOki,
            @Value("${cod_GEO_FR}") Integer codGeoFr) {
        this.dbUrl = dbUrl;
        this.connectionProperties = buildConnectionProperties(dbUsername, dbPassword);
        this.codGeoFyb = codGeoFyb;
        this.codGeoSana = codGeoSana;
        this.codGeoOki = codGeoOki;
        this.codGeoFr = codGeoFr;
    }

    /**
     * Inicializa la cache de bancos al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de AD_TIPO_PAGO cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<String, Set<String>> refreshed = loadAllowedBanksFromDb();
            this.allowedBanksByProviderAndChain = Map.copyOf(refreshed);
            log.info("Cache AD_TIPO_PAGO actualizada. Combinaciones provider-chain: {}", refreshed.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache AD_TIPO_PAGO. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene los codigos de banco activos por proveedor y cadena.
     *
     * @param paymentProviderCode codigo de proveedor de pago
     * @param chain cadena solicitada
     * @return conjunto de codigos de banco permitidos
     */
    public Set<String> findAllowedBankCodes(Integer paymentProviderCode, Integer chain) {
        if (paymentProviderCode == null || chain == null) {
            return Set.of();
        }
        String key = key(paymentProviderCode, chain);
        return allowedBanksByProviderAndChain.getOrDefault(key, Set.of());
    }

    private Map<String, Set<String>> loadAllowedBanksFromDb() throws Exception {
        Map<String, Set<String>> temp = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_BANKS)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bankCode = rs.getString("CODIGO");
                    Integer providerCode = rs.getInt("CODIGO_BILLETERA_DIGITAL");
                    if (bankCode == null || bankCode.isBlank()) {
                        continue;
                    }
                    String normalizedBankCode = bankCode.trim();
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_FYB"))) {
                        addBank(temp, providerCode, codGeoFyb, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_SANA"))) {
                        addBank(temp, providerCode, codGeoSana, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_OKI"))) {
                        addBank(temp, providerCode, codGeoOki, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_FR"))) {
                        addBank(temp, providerCode, codGeoFr, normalizedBankCode);
                    }
                }
            }
        }
        Map<String, Set<String>> immutable = new HashMap<>();
        temp.forEach((k, v) -> immutable.put(k, Set.copyOf(v)));
        return immutable;
    }

    private void addBank(Map<String, Set<String>> target, Integer providerCode, Integer chain, String bankCode) {
        if (providerCode == null || chain == null || bankCode == null || bankCode.isBlank()) {
            return;
        }
        String key = key(providerCode, chain);
        target.computeIfAbsent(key, value -> new HashSet<>()).add(bankCode);
    }

    private String key(Integer paymentProviderCode, Integer chain) {
        return paymentProviderCode + "|" + chain;
    }

    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        return props;
    }
}
