package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.GatewayWebServiceDefinitionService;

class GatewayWebServiceDefinitionServiceTest {

    @Test
    void refreshCacheLoadsDefaultsAndQueryDefinitions() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT WS.CODIGO_BILLETERA, WS.WS_KEY, D.DEFAULT_CLAVE, "
                + "D.DEFAULT_VALOR_TEXTO, D.DEFAULT_VALOR_NUM, D.DEFAULT_VALOR_FECHA, D.TIPO_DEF, "
                + "D.DEFAULT_VALOR_SISTEMA "
                + "FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS D "
                + "JOIN TUKUNAFUNC.IN_PASARELA_WS WS ON WS.ID_WS = D.ID_WS "
                + "ORDER BY WS.CODIGO_BILLETERA, WS.WS_KEY, D.ID_DEFAULT")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(235689, 235689, 235689, 235689, 235689);
        when(resultSet.wasNull()).thenReturn(false, false, false, false, false);
        when(resultSet.getString("WS_KEY"))
                .thenReturn("payments", "payments", "payments", "direct-online-payment-requests",
                        "direct-online-payment-requests");
        when(resultSet.getString("DEFAULT_CLAVE"))
                .thenReturn("operation_id", "request_datetime", "limit", "application_id", "payment_ok_url");
        when(resultSet.getString("TIPO_DEF")).thenReturn("QUERY", "QUERY", "QUERY", "DEFAULTS", "DEFAULTS");
        when(resultSet.getString("DEFAULT_VALOR_SISTEMA")).thenReturn("operation_id", "now", null, null, null);
        when(resultSet.getString("DEFAULT_VALOR_TEXTO"))
                .thenReturn(null, null, null, null, "https://www.safetypay.com/success.com");
        when(resultSet.getBigDecimal("DEFAULT_VALOR_NUM"))
                .thenReturn(null, null, new BigDecimal("100"), new BigDecimal("7"), null);
        when(resultSet.getTimestamp("DEFAULT_VALOR_FECHA")).thenReturn(null, null, null, null, null);

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(org.mockito.ArgumentMatchers.any(DatabaseExecutor.ConnectionConsumer.class));

        GatewayWebServiceDefinitionService service = new GatewayWebServiceDefinitionService(databaseExecutor);
        service.refreshCache();

        Map<String, String> query = service.getQueryParams(
                235689,
                "payments",
                Map.of("operation_id", "OP-123", "now", "2026-02-27T12:00:00"));
        assertThat(query).containsEntry("operation_id", "OP-123");
        assertThat(query).containsEntry("request_datetime", "2026-02-27T12:00:00");
        assertThat(query).containsEntry("limit", "100");

        Map<String, Object> defaults = service.getDefaults(
                235689,
                "direct-online-payment-requests",
                Map.of());
        assertThat(defaults).containsEntry("application_id", "7");
        assertThat(defaults).containsEntry("payment_ok_url", "https://www.safetypay.com/success.com");
    }
}
