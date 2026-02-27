package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceConfigService;

class GatewayWebServiceConfigServiceTest {

    @Test
    void refreshCacheLoadsActiveRestConfigByProviderAndWsKey() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT CODIGO_BILLETERA, WS_KEY, ENABLED, "
                + "TIPO_CONEXION, METODO_HTTP, TIPO_REQUEST, URL AS SERVICE_URI "
                + "FROM TUKUNAFUNC.IN_PASARELA_WS")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(235689, 235689, 235689);
        when(resultSet.wasNull()).thenReturn(false, false, false);
        when(resultSet.getString("WS_KEY"))
                .thenReturn("direct-online-payment-requests", "payments", "merchant-events");
        when(resultSet.getString("ENABLED")).thenReturn("S", "N", "S");
        when(resultSet.getString("TIPO_CONEXION")).thenReturn("REST", "REST", "SOAP");
        when(resultSet.getString("METODO_HTTP")).thenReturn("POST", "GET", "POST");
        when(resultSet.getString("TIPO_REQUEST")).thenReturn("JSON", "PARAMETROS", "JSON");
        when(resultSet.getString("SERVICE_URI"))
                .thenReturn("https://example.com/direct", "https://example.com/payments", "https://example.com/events");

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(org.mockito.ArgumentMatchers.any(DatabaseExecutor.ConnectionConsumer.class));

        GatewayWebServiceConfigService service = new GatewayWebServiceConfigService(databaseExecutor);
        service.refreshCache();

        assertThat(service.isActive(235689, "direct-online-payment-requests")).isTrue();
        assertThat(service.getActiveConfig(235689, "direct-online-payment-requests"))
                .isPresent()
                .get()
                .extracting(GatewayWebServiceConfigService.WebServiceConfig::method,
                        GatewayWebServiceConfigService.WebServiceConfig::uri)
                .containsExactly("POST", "https://example.com/direct");
        assertThat(service.isActive(235689, "payments")).isFalse();
        assertThat(service.isActive(235689, "merchant-events")).isFalse();
    }
}
