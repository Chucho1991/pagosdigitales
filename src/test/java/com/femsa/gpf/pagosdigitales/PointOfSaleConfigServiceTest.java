package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PointOfSaleConfigService;

class PointOfSaleConfigServiceTest {

    private static final String SELECT_POINT_OF_SALES = "SELECT CODIGO_BILLETERA, CODIGO_CADENA, "
            + "CODIGO_LOCAL, CODIGO_POS, POINT_OF_SALE "
            + "FROM TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA "
            + "WHERE ACTIVO = 'S' "
            + "ORDER BY CODIGO_BILLETERA, CODIGO_CADENA, CODIGO_LOCAL, CODIGO_POS";

    @Test
    void refreshCacheLoadsActivePointOfSalesAndFindUsesMemory() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(SELECT_POINT_OF_SALES)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(300002, 300002);
        when(resultSet.getInt("CODIGO_CADENA")).thenReturn(60, 60);
        when(resultSet.getInt("CODIGO_LOCAL")).thenReturn(148, 148);
        when(resultSet.getInt("CODIGO_POS")).thenReturn(90, 1);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("POINT_OF_SALE")).thenReturn(" 5 ", "6");
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        PointOfSaleConfigService service = new PointOfSaleConfigService(databaseExecutor);
        boolean refreshed = service.refreshCache();

        assertThat(refreshed).isTrue();
        assertThat(service.findPointOfSale(300002, 60, 148, 90)).contains("5");
        assertThat(service.findPointOfSale(300002, 60, 148, 1)).contains("6");
        assertThat(service.findPointOfSale(300002, 60, 999, 1)).isEmpty();
        verify(databaseExecutor, times(1)).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));
    }

    @Test
    void refreshCacheKeepsPreviousValuesWhenDatabaseRefreshFails() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(SELECT_POINT_OF_SALES)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(300002);
        when(resultSet.getInt("CODIGO_CADENA")).thenReturn(60);
        when(resultSet.getInt("CODIGO_LOCAL")).thenReturn(148);
        when(resultSet.getInt("CODIGO_POS")).thenReturn(90);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getString("POINT_OF_SALE")).thenReturn("5");
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).doThrow(new IllegalStateException("Base no disponible"))
                .when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        PointOfSaleConfigService service = new PointOfSaleConfigService(databaseExecutor);
        boolean firstRefresh = service.refreshCache();
        boolean secondRefresh = service.refreshCache();

        assertThat(firstRefresh).isTrue();
        assertThat(secondRefresh).isFalse();
        assertThat(service.findPointOfSale(300002, 60, 148, 90)).contains("5");
        verify(databaseExecutor, times(2)).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));
    }
}
