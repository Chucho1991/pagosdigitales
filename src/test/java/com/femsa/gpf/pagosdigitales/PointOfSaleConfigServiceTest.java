package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PointOfSaleConfigService;

class PointOfSaleConfigServiceTest {

    private static final String SELECT_POINT_OF_SALE = "SELECT POINT_OF_SALE "
            + "FROM TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA "
            + "WHERE CODIGO_BILLETERA = ? "
            + "AND CODIGO_CADENA = ? "
            + "AND CODIGO_LOCAL = ? "
            + "AND CODIGO_POS = ? "
            + "AND ACTIVO = 'S'";

    @Test
    void findPointOfSaleUsesProviderChainStoreAndPos() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(SELECT_POINT_OF_SALE)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("POINT_OF_SALE")).thenReturn(" 5 ");
        when(databaseExecutor.withConnection(any(DatabaseExecutor.ConnectionCallback.class)))
                .thenAnswer(invocation -> {
                    DatabaseExecutor.ConnectionCallback<Optional<String>> callback = invocation.getArgument(0);
                    return callback.execute(connection);
                });

        PointOfSaleConfigService service = new PointOfSaleConfigService(databaseExecutor);

        assertThat(service.findPointOfSale(300002, 60, 148, 90)).contains("5");
        verify(statement).setInt(1, 300002);
        verify(statement).setInt(2, 60);
        verify(statement).setInt(3, 148);
        verify(statement).setInt(4, 90);
    }

    @Test
    void findPointOfSaleReturnsEmptyWhenCombinationDoesNotExist() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(SELECT_POINT_OF_SALE)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(databaseExecutor.withConnection(any(DatabaseExecutor.ConnectionCallback.class)))
                .thenAnswer(invocation -> {
                    DatabaseExecutor.ConnectionCallback<Optional<String>> callback = invocation.getArgument(0);
                    return callback.execute(connection);
                });

        PointOfSaleConfigService service = new PointOfSaleConfigService(databaseExecutor);

        assertThat(service.findPointOfSale(300002, 60, 999, 1)).isEmpty();
    }
}
