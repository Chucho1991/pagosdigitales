package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.domain.model.CacheRefreshResult;
import com.femsa.gpf.pagosdigitales.domain.service.ProvidersPayService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.BanksCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseCacheRefreshAdapter;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ErrorMappingCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.IssuerCommissionCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.LocalBanksCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PointOfSaleConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.SafetypayConfirmationConfigService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class DatabaseCacheRefreshAdapterTest {

    @Test
    void refreshAllContinuesAndReportsEachCacheResult() {
        ProvidersPayService providers = successful(mock(ProvidersPayService.class));
        BanksCatalogService banks = successful(mock(BanksCatalogService.class));
        LocalBanksCatalogService localBanks = successful(mock(LocalBanksCatalogService.class));
        ErrorMappingCatalogService errors = successful(mock(ErrorMappingCatalogService.class));
        GatewayWebServiceConfigService webServices = successful(mock(GatewayWebServiceConfigService.class));
        GatewayWebServiceDefinitionService definitions = successful(mock(GatewayWebServiceDefinitionService.class));
        ProviderHeaderService headers = successful(mock(ProviderHeaderService.class));
        ServiceMappingConfigService mappings = successful(mock(ServiceMappingConfigService.class));
        PointOfSaleConfigService pointOfSales = successful(mock(PointOfSaleConfigService.class));
        SafetypayConfirmationConfigService safetypay = successful(mock(SafetypayConfirmationConfigService.class));
        IssuerCommissionCatalogService commissions = mock(IssuerCommissionCatalogService.class);
        when(commissions.refreshCache()).thenReturn(false);

        DatabaseCacheRefreshAdapter adapter = new DatabaseCacheRefreshAdapter(
                providers, banks, localBanks, errors, webServices, definitions, headers, mappings, pointOfSales,
                safetypay, commissions);

        List<CacheRefreshResult> results = adapter.refreshAll();

        assertThat(results).hasSize(11);
        assertThat(results).filteredOn(CacheRefreshResult::successful).hasSize(10);
        assertThat(results).extracting(CacheRefreshResult::cacheName).contains("point-of-sales");
        assertThat(results).filteredOn(result -> !result.successful())
                .extracting(CacheRefreshResult::cacheName)
                .containsExactly("issuer-commissions");
    }

    private ProvidersPayService successful(ProvidersPayService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private BanksCatalogService successful(BanksCatalogService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private LocalBanksCatalogService successful(LocalBanksCatalogService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private ErrorMappingCatalogService successful(ErrorMappingCatalogService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private GatewayWebServiceConfigService successful(GatewayWebServiceConfigService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private GatewayWebServiceDefinitionService successful(GatewayWebServiceDefinitionService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private ProviderHeaderService successful(ProviderHeaderService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private ServiceMappingConfigService successful(ServiceMappingConfigService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private SafetypayConfirmationConfigService successful(SafetypayConfirmationConfigService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }

    private PointOfSaleConfigService successful(PointOfSaleConfigService service) {
        when(service.refreshCache()).thenReturn(true);
        return service;
    }
}
