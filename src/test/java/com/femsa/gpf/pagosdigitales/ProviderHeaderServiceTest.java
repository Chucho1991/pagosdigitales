package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ProviderHeaderService;

class ProviderHeaderServiceTest {

    @Test
    void refreshCacheLoadsHeadersByProviderCode() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT CODIGO_BILLETERA, HEADER_NOMBRE, HEADER_VALOR "
                + "FROM TUKUNAFUNC.IN_PASARELA_HEADERS")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(235689, 235689);
        when(resultSet.wasNull()).thenReturn(false, false);
        when(resultSet.getString("HEADER_NOMBRE")).thenReturn("X-Api-Key", "X-Version");
        when(resultSet.getString("HEADER_VALOR")).thenReturn("api-key-value", "20200803");

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(org.mockito.ArgumentMatchers.any(DatabaseExecutor.ConnectionConsumer.class));

        ProviderHeaderService service = new ProviderHeaderService(databaseExecutor);

        service.refreshCache();

        Map<String, String> headers = service.getHeadersByProviderCode(235689);
        assertThat(headers).containsEntry("X-Api-Key", "api-key-value");
        assertThat(headers).containsEntry("X-Version", "20200803");
        assertThat(service.getHeadersByProviderCode(999999)).isEmpty();
    }
}
