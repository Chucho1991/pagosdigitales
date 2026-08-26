package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.application.ports.out.CacheRefreshPort;
import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;

import lombok.extern.log4j.Log4j2;

/**
 * Adaptador que ejecuta los refrescos de caches respaldadas por tablas Oracle.
 */
@Log4j2
@Component
public class DatabaseCacheRefreshAdapter implements CacheRefreshPort {

    private final ProvidersPayService providersPayService;
    private final BanksCatalogService banksCatalogService;
    private final LocalBanksCatalogService localBanksCatalogService;
    private final ErrorMappingCatalogService errorMappingCatalogService;
    private final GatewayWebServiceConfigService webServiceConfigService;
    private final GatewayWebServiceDefinitionService webServiceDefinitionService;
    private final ProviderHeaderService providerHeaderService;
    private final ServiceMappingConfigService serviceMappingConfigService;
    private final PointOfSaleConfigService pointOfSaleConfigService;
    private final SafetypayConfirmationConfigService safetypayConfigService;
    private final IssuerCommissionCatalogService issuerCommissionCatalogService;

    /**
     * Crea el adaptador con todos los servicios que mantienen caches locales.
     *
     * @param providersPayService cache de proveedores
     * @param banksCatalogService cache de bancos habilitados
     * @param localBanksCatalogService cache de bancos locales
     * @param errorMappingCatalogService cache de mapeos de error
     * @param webServiceConfigService cache de endpoints externos
     * @param webServiceDefinitionService cache de definiciones externas
     * @param providerHeaderService cache de headers externos
     * @param serviceMappingConfigService cache de mapeos de servicios
     * @param pointOfSaleConfigService cache de puntos de venta externos
     * @param safetypayConfigService cache de confirmacion SafetyPay
     * @param issuerCommissionCatalogService cache de comisiones emisoras
     */
    public DatabaseCacheRefreshAdapter(ProvidersPayService providersPayService,
            BanksCatalogService banksCatalogService,
            LocalBanksCatalogService localBanksCatalogService,
            ErrorMappingCatalogService errorMappingCatalogService,
            GatewayWebServiceConfigService webServiceConfigService,
            GatewayWebServiceDefinitionService webServiceDefinitionService,
            ProviderHeaderService providerHeaderService,
            ServiceMappingConfigService serviceMappingConfigService,
            PointOfSaleConfigService pointOfSaleConfigService,
            SafetypayConfirmationConfigService safetypayConfigService,
            IssuerCommissionCatalogService issuerCommissionCatalogService) {
        this.providersPayService = providersPayService;
        this.banksCatalogService = banksCatalogService;
        this.localBanksCatalogService = localBanksCatalogService;
        this.errorMappingCatalogService = errorMappingCatalogService;
        this.webServiceConfigService = webServiceConfigService;
        this.webServiceDefinitionService = webServiceDefinitionService;
        this.providerHeaderService = providerHeaderService;
        this.serviceMappingConfigService = serviceMappingConfigService;
        this.pointOfSaleConfigService = pointOfSaleConfigService;
        this.safetypayConfigService = safetypayConfigService;
        this.issuerCommissionCatalogService = issuerCommissionCatalogService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CacheRefreshResult> refreshAll() {
        List<CacheRefreshResult> results = new ArrayList<>();
        results.add(refresh("payment-providers", List.of("AD_BILLETERAS_DIGITALES"),
                providersPayService::refreshCache));
        results.add(refresh("banks", List.of("AD_CANAL", "AD_CANAL_TIPO_PAGO", "AD_TIPO_PAGO"),
                banksCatalogService::refreshCache));
        results.add(refresh("local-banks", List.of("AD_TIPO_PAGO"), localBanksCatalogService::refreshCache));
        results.add(refresh("error-mappings", List.of("AD_MAPEO_ERRORES"), errorMappingCatalogService::refreshCache));
        results.add(refresh("gateway-web-services", List.of("IN_PASARELA_WS"),
                webServiceConfigService::refreshCache));
        results.add(refresh("gateway-web-service-definitions", List.of("IN_PASARELA_WS_DEFS"),
                webServiceDefinitionService::refreshCache));
        results.add(refresh("provider-headers", List.of("IN_PASARELA_HEADERS"), providerHeaderService::refreshCache));
        results.add(refresh("service-mappings", List.of("AD_MAPEO_SERVICIOS"),
                serviceMappingConfigService::refreshCache));
        results.add(refresh("point-of-sales", List.of("IN_PASARELA_PUNTO_VENTA"),
                pointOfSaleConfigService::refreshCache));
        results.add(refresh("safetypay-confirmation", List.of("IN_SAFETYPAY_CFG"),
                safetypayConfigService::refreshCache));
        results.add(refresh("issuer-commissions", List.of("AD_TIPO_PAGO", "AD_COMISION_TIPOPAGO"),
                issuerCommissionCatalogService::refreshCache));
        return List.copyOf(results);
    }

    private CacheRefreshResult refresh(String cacheName, List<String> tables, BooleanSupplier operation) {
        try {
            return new CacheRefreshResult(cacheName, tables, operation.getAsBoolean());
        } catch (RuntimeException e) {
            log.error("Fallo inesperado refrescando cache {}. Se continua con las demas caches.", cacheName, e);
            return new CacheRefreshResult(cacheName, tables, false);
        }
    }
}
