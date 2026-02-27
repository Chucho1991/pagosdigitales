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
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ServiceMappingConfigService;

class ServiceMappingConfigServiceTest {

    @Test
    void refreshCacheLoadsMappingsAndResolvesProviderSpecificAndDefault() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT ID_MAPEO_SERVICIO, CODIGO_BILLETERA, "
                + "APP_SERVICE_KEY, APP_OPERATION, DIRECCION, SECCION_APP, ATRIBUTO_APP, "
                + "SECCION_EXT, ATRIBUTO_EXT, ORDEN_APLICACION, ACTIVO "
                + "FROM TUKUNAFUNC.AD_MAPEO_SERVICIOS")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, false);
        when(resultSet.getInt("CODIGO_BILLETERA")).thenReturn(235689, 235689, 235689, 235689);
        when(resultSet.wasNull()).thenReturn(false, false, false, false);
        when(resultSet.getString("ACTIVO")).thenReturn("S", "S", "S", "S");
        when(resultSet.getString("APP_SERVICE_KEY")).thenReturn("payments", "payments", "payments", "payments");
        when(resultSet.getString("APP_OPERATION")).thenReturn("DEFAULT", "DEFAULT", "PAYSAFE", "DEFAULT");
        when(resultSet.getString("DIRECCION")).thenReturn("RESPONSE", "RESPONSE", "RESPONSE", "ERROR");
        when(resultSet.getString("SECCION_APP")).thenReturn("BODY", "BODY", "BODY", "BODY");
        when(resultSet.getString("ATRIBUTO_APP")).thenReturn("requestId", "responseDatetime", "requestId", "error");
        when(resultSet.getString("SECCION_EXT")).thenReturn("BODY", "BODY", "BODY", "BODY");
        when(resultSet.getString("ATRIBUTO_EXT")).thenReturn("request_id", "response_datetime", "request_id_paysafe", "error.payload");
        when(resultSet.getInt("ORDEN_APLICACION")).thenReturn(1, 2, 1, 1);
        when(resultSet.getLong("ID_MAPEO_SERVICIO")).thenReturn(1L, 2L, 3L, 4L);

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(org.mockito.ArgumentMatchers.any(DatabaseExecutor.ConnectionConsumer.class));

        ServiceMappingConfigService service = new ServiceMappingConfigService(databaseExecutor);
        service.refreshCache();

        Map<String, String> paysafe = service.getResponseBodyMappings(235689, "payments", "paysafe");
        assertThat(paysafe).containsExactly(Map.entry("requestId", "request_id_paysafe"));

        Map<String, String> fallback = service.getResponseBodyMappings(235689, "payments", "pichincha");
        assertThat(fallback).containsExactly(
                Map.entry("requestId", "request_id"),
                Map.entry("responseDatetime", "response_datetime"));

        String errorPath = service.getErrorPath(235689, "payments", "pichincha");
        assertThat(errorPath).isEqualTo("error.payload");
    }
}
