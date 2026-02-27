package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver configuracion de confirmation de SafetyPay desde BD.
 */
@Log4j2
@Service
public class SafetypayConfirmationConfigService {

    private static final String SELECT_CONFIRMATION_CONFIG = "SELECT C.CODIGO_BILLETERA, "
            + "C.NOMBRE_PROVEEDOR, C.ENABLED, C.API_KEY, C.SECRET, C.SIGNATURE_MODE, C.ALLOWED_IPS "
            + "FROM TUKUNAFUNC.IN_SAFETYPAY_CFG C "
            + "WHERE C.ACTIVO = 'S' "
            + "ORDER BY C.CODIGO_BILLETERA";

    private final DatabaseExecutor databaseExecutor;
    private volatile Map<String, ProviderConfig> providers = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public SafetypayConfirmationConfigService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Inicializa cache al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            this.providers = Map.copyOf(loadFromDb());
            log.info("Cache de confirmation SafetyPay actualizada. Proveedores: {}", providers.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache de confirmation SafetyPay. Se conserva cache anterior.", e);
        }
    }

    /**
     * Indica si el endpoint tiene al menos un proveedor activo.
     *
     * @return true cuando existe configuracion activa
     */
    public boolean isEnabled() {
        return providers.values().stream().anyMatch(ProviderConfig::enabled);
    }

    /**
     * Resuelve proveedor por api key. Si no hay match retorna paysafe o primer activo.
     *
     * @param apiKey api key entrante
     * @return proveedor resuelto
     */
    public Optional<ProviderConfig> resolveProvider(String apiKey) {
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<ProviderConfig> byApiKey = providers.values().stream()
                    .filter(ProviderConfig::enabled)
                    .filter(p -> apiKey.equals(p.apiKey()))
                    .findFirst();
            if (byApiKey.isPresent()) {
                return byApiKey;
            }
        }
        ProviderConfig paysafe = providers.get("paysafe");
        if (paysafe != null && paysafe.enabled()) {
            return Optional.of(paysafe);
        }
        return providers.values().stream().filter(ProviderConfig::enabled).findFirst();
    }

    private Map<String, ProviderConfig> loadFromDb() throws Exception {
        Map<String, ProviderConfig> resolved = new LinkedHashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_CONFIRMATION_CONFIG);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer providerCode = rs.getInt("CODIGO_BILLETERA");
                    if (rs.wasNull()) {
                        continue;
                    }
                    String providerName = normalizeProviderName(rs.getString("NOMBRE_PROVEEDOR"));
                    if (providerName.isBlank()) {
                        providerName = String.valueOf(providerCode);
                    }
                    String signatureMode = trimToEmpty(rs.getString("SIGNATURE_MODE"));
                    resolved.put(providerName, new ProviderConfig(
                            providerCode,
                            providerName,
                            "S".equalsIgnoreCase(trimToEmpty(rs.getString("ENABLED"))),
                            trimToEmpty(rs.getString("API_KEY")),
                            trimToEmpty(rs.getString("SECRET")),
                            signatureMode.isBlank() ? "SHA256" : signatureMode,
                            parseIps(trimToEmpty(rs.getString("ALLOWED_IPS")))));
                }
            }
        });
        return resolved;
    }

    private List<String> parseIps(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] tokens = normalized.split(",");
        List<String> ips = new ArrayList<>();
        for (String token : tokens) {
            String ip = token.replace("\"", "").trim();
            if (!ip.isBlank()) {
                ips.add(ip);
            }
        }
        return ips;
    }

    private String normalizeProviderName(String providerName) {
        String normalized = trimToEmpty(providerName).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.contains("paysafe") || normalized.contains("safetypay")) {
            return "paysafe";
        }
        if (normalized.contains("pichincha")) {
            return "pichincha";
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Configuracion de confirmation por proveedor.
     *
     * @param providerCode codigo de proveedor
     * @param providerName nombre normalizado
     * @param enabled indica si esta activo
     * @param apiKey api key esperada
     * @param secret secreto de firma
     * @param signatureMode modo de firma
     * @param allowedIps lista de ips permitidas
     */
    public record ProviderConfig(
            Integer providerCode,
            String providerName,
            boolean enabled,
            String apiKey,
            String secret,
            String signatureMode,
            List<String> allowedIps) {
    }

}
