package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para consultar bancos permitidos por proveedor y cadena.
 */
@Log4j2
@Service
public class BanksCatalogService {

    private static final String SELECT_ACTIVE_BANKS_BY_CHAIN = "SELECT CODIGO, CODIGO_BILLETERA_DIGITAL, "
            + "NVL(CADENA_FYB, 'N') CADENA_FYB, NVL(CADENA_SANA, 'N') CADENA_SANA, "
            + "NVL(CADENA_OKI, 'N') CADENA_OKI, NVL(CADENA_FR, 'N') CADENA_FR "
            + "FROM TUKUNAFUNC.AD_TIPO_PAGO "
            + "WHERE NVL(ACTIVO, 'N') = 'S'";

    private static final String SELECT_ACTIVE_CHANNEL_BANKS = "SELECT "
            + "A.DESCRIPCION AS CANAL, "
            + "A.ACTIVO AS ESTADO, "
            + "B.CODIGO_TIPOPAGO AS ID_BANCO, "
            + "C.CODIGO_BILLETERA_DIGITAL AS ID_PROVEEDOR_PAGO "
            + "FROM TUKUNAFUNC.AD_CANAL A, "
            + "TUKUNAFUNC.AD_CANAL_TIPO_PAGO B, "
            + "TUKUNAFUNC.AD_TIPO_PAGO C "
            + "WHERE A.CODIGO = B.CODIGO_CANAL "
            + "AND B.CODIGO_TIPOPAGO = C.CODIGO "
            + "AND A.ACTIVO = 'S'";

    private final String dbUrl;
    private final Properties connectionProperties;
    private final Integer codGeoFyb;
    private final Integer codGeoSana;
    private final Integer codGeoOki;
    private final Integer codGeoFr;
    private volatile Map<String, Set<String>> allowedBanksByProviderAndChain = Map.of();
    private volatile Map<String, Set<String>> allowedBanksByProviderAndChannel = Map.of();

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
            this.allowedBanksByProviderAndChain = Map.copyOf(loadAllowedBanksByChainFromDb());
            this.allowedBanksByProviderAndChannel = Map.copyOf(loadAllowedBanksByChannelFromDb());
            log.info("Cache AD_CANAL/AD_CANAL_TIPO_PAGO/AD_TIPO_PAGO actualizada. Combinaciones provider-canal: {}",
                    allowedBanksByProviderAndChannel.size());
        } catch (Exception e) {
            log.error(
                    "No fue posible refrescar cache AD_CANAL/AD_CANAL_TIPO_PAGO/AD_TIPO_PAGO. Se conserva cache anterior.",
                    e);
        }
    }

    /**
     * Obtiene los codigos de banco activos por proveedor, cadena y canal.
     *
     * @param paymentProviderCode codigo de proveedor de pago
     * @param chain cadena solicitada
     * @param channelPos canal del request
     * @return conjunto de codigos de banco permitidos
     */
    public Set<String> findAllowedBankCodes(Integer paymentProviderCode, Integer chain, String channelPos) {
        if (paymentProviderCode == null || chain == null || channelPos == null || channelPos.isBlank()) {
            return Set.of();
        }
        Set<String> byChain = allowedBanksByProviderAndChain.getOrDefault(keyByChain(paymentProviderCode, chain), Set.of());
        Set<String> byChannel = allowedBanksByProviderAndChannel.getOrDefault(keyByChannel(paymentProviderCode, channelPos),
                Set.of());
        if (byChain.isEmpty() || byChannel.isEmpty()) {
            return Set.of();
        }
        Set<String> intersection = new HashSet<>(byChain);
        intersection.retainAll(byChannel);
        return Set.copyOf(intersection);
    }

    private Map<String, Set<String>> loadAllowedBanksByChainFromDb() throws Exception {
        Map<String, Set<String>> temp = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_BANKS_BY_CHAIN)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bankCode = rs.getString("CODIGO");
                    Integer providerCode = rs.getInt("CODIGO_BILLETERA_DIGITAL");
                    if (bankCode == null || bankCode.isBlank()) {
                        continue;
                    }
                    String normalizedBankCode = bankCode.trim();
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_FYB"))) {
                        addBankByChain(temp, providerCode, codGeoFyb, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_SANA"))) {
                        addBankByChain(temp, providerCode, codGeoSana, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_OKI"))) {
                        addBankByChain(temp, providerCode, codGeoOki, normalizedBankCode);
                    }
                    if ("S".equalsIgnoreCase(rs.getString("CADENA_FR"))) {
                        addBankByChain(temp, providerCode, codGeoFr, normalizedBankCode);
                    }
                }
            }
        }
        return immutableCopy(temp);
    }

    private Map<String, Set<String>> loadAllowedBanksByChannelFromDb() throws Exception {
        Map<String, Set<String>> temp = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(dbUrl, connectionProperties);
                PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_CHANNEL_BANKS)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String channel = rs.getString("CANAL");
                    String bankCode = rs.getString("ID_BANCO");
                    Integer providerCode = rs.getInt("ID_PROVEEDOR_PAGO");
                    if (channel == null || channel.isBlank() || bankCode == null || bankCode.isBlank()) {
                        continue;
                    }
                    addBankByChannel(temp, providerCode, channel, bankCode.trim());
                }
            }
        }
        return immutableCopy(temp);
    }

    private Map<String, Set<String>> immutableCopy(Map<String, Set<String>> source) {
        Map<String, Set<String>> immutable = new HashMap<>();
        source.forEach((k, v) -> immutable.put(k, Set.copyOf(v)));
        return immutable;
    }

    private void addBankByChain(Map<String, Set<String>> target, Integer providerCode, Integer chain, String bankCode) {
        if (providerCode == null || chain == null || bankCode == null || bankCode.isBlank()) {
            return;
        }
        String key = keyByChain(providerCode, chain);
        target.computeIfAbsent(key, value -> new HashSet<>()).add(bankCode);
    }

    private void addBankByChannel(Map<String, Set<String>> target, Integer providerCode, String channel, String bankCode) {
        if (providerCode == null || channel == null || channel.isBlank() || bankCode == null || bankCode.isBlank()) {
            return;
        }
        String key = keyByChannel(providerCode, channel);
        target.computeIfAbsent(key, value -> new HashSet<>()).add(bankCode);
    }

    private String keyByChannel(Integer paymentProviderCode, String channel) {
        return paymentProviderCode + "|" + normalizeChannel(channel);
    }

    private String keyByChain(Integer paymentProviderCode, Integer chain) {
        return paymentProviderCode + "|" + chain;
    }

    private String normalizeChannel(String channel) {
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        return props;
    }
}
