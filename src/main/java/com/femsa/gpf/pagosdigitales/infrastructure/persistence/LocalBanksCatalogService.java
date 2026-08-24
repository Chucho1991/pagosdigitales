package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.api.dto.BankItem;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio para resolver bancos locales de proveedores con TIPO_BANCO = 'INTERNO'.
 *
 * <p>Lee la tabla AD_TIPO_PAGO y construye objetos BankItem con la misma estructura
 * que devuelven los proveedores externos, permitiendo que el BanksController
 * responda sin necesidad de llamar a un servicio externo.</p>
 */
@Log4j2
@Service
public class LocalBanksCatalogService {

    private static final String SELECT_LOCAL_BANKS = "SELECT CODIGO_ESTABLECIMIENTO, DESCRIPCION, "
            + "CODIGO_BILLETERA_DIGITAL, MINIMO, MAXIMO "
            + "FROM TUKUNAFUNC.AD_TIPO_PAGO "
            + "WHERE NVL(ACTIVO, 'N') = 'S'";

    private static final String CHANNEL_QRCODE = "QRCode";
    private static final String CHANNEL_DEFAULT = "Online";

    private final DatabaseExecutor databaseExecutor;
    private final ProvidersPayService providersPayService;
    private volatile Map<Integer, List<BankItem>> banksByProviderCode = Map.of();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     * @param providersPayService servicio de proveedores para resolver nombres
     */
    public LocalBanksCatalogService(DatabaseExecutor databaseExecutor,
            ProvidersPayService providersPayService) {
        this.databaseExecutor = databaseExecutor;
        this.providersPayService = providersPayService;
    }

    /**
     * Inicializa la cache de bancos locales al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache de bancos locales cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            Map<Integer, List<BankItem>> loaded = loadLocalBanksFromDb();
            this.banksByProviderCode = Map.copyOf(loaded);
            log.info("Cache de bancos locales actualizada. Proveedores con bancos locales: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache de bancos locales. Se conserva cache anterior.", e);
        }
    }

    /**
     * Obtiene los bancos locales configurados para un proveedor.
     *
     * @param providerCode codigo de billetera digital
     * @return lista de bancos en formato BankItem; lista vacia si no hay configuracion
     */
    public List<BankItem> getBanksByProviderCode(Integer providerCode) {
        if (providerCode == null) {
            return List.of();
        }
        return banksByProviderCode.getOrDefault(providerCode, List.of());
    }

    private Map<Integer, List<BankItem>> loadLocalBanksFromDb() throws Exception {
        Map<Integer, List<BankItem>> temp = new HashMap<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_LOCAL_BANKS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer providerCode = rs.getInt("CODIGO_BILLETERA_DIGITAL");
                    if (rs.wasNull()) {
                        continue;
                    }
                    String bankId = rs.getString("CODIGO_ESTABLECIMIENTO");
                    String bankName = rs.getString("DESCRIPCION");
                    BigDecimal minimo = rs.getBigDecimal("MINIMO");
                    BigDecimal maximo = rs.getBigDecimal("MAXIMO");

                    if (bankId == null || bankId.isBlank()) {
                        continue;
                    }

                    BankItem item = new BankItem();
                    item.setBank_id(bankId.trim());
                    item.setBank_name(bankName != null ? bankName.trim() : "");
                    item.setBank_commercial_name(bankName != null ? bankName.trim() : "");
                    item.setBank_country_code("ECU");
                    item.setBank_type("Gateway");
                    item.setShow_standalone(true);
                    item.setChannel(resolveChannel(providerCode));
                    item.setChannel_tag("Person");
                    item.setStatus(1);
                    item.setAccess_type(2);
                    item.setLanguage_code("ES");
                    item.setWorking_hours("");
                    item.setDisclaimer("");
                    item.setDefault_currency_code("USD");
                    item.setLocations_url("");
                    item.setAmount_limits(buildAmountLimits(minimo, maximo));

                    temp.computeIfAbsent(providerCode, k -> new ArrayList<>()).add(item);
                }
            }
        });
        Map<Integer, List<BankItem>> immutable = new HashMap<>();
        temp.forEach((code, banks) -> immutable.put(code, Collections.unmodifiableList(banks)));
        return immutable;
    }

    /**
     * Resuelve el canal segun el nombre normalizado del proveedor.
     *
     * @param providerCode codigo de billetera digital
     * @return canal correspondiente al proveedor
     */
    private String resolveChannel(Integer providerCode) {
        String providerName = providersPayService.getProviderNameByCode(providerCode);
        if (providerName.contains("jep") || providerName.contains("jepfaster")) {
            return CHANNEL_QRCODE;
        }
        if (providerName.contains("deuna")) {
            return CHANNEL_QRCODE;
        }
        return CHANNEL_DEFAULT;
    }

    private List<Map<String, Object>> buildAmountLimits(BigDecimal minimo, BigDecimal maximo) {
        Map<String, Object> limit = new HashMap<>();
        limit.put("currency_code", "USD");
        limit.put("min_limit", minimo != null ? minimo.toPlainString() : "0.00");
        limit.put("max_limit", maximo != null ? maximo.toPlainString() : "10000.00");
        return List.of(Collections.unmodifiableMap(limit));
    }
}
