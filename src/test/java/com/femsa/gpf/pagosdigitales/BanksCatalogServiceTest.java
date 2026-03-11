package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.BanksCatalogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;

class BanksCatalogServiceTest {

    @Test
    void findMinimumReturnsConfiguredValueByProviderAndPaymentType() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement chainStatement = mock(PreparedStatement.class);
        PreparedStatement channelStatement = mock(PreparedStatement.class);
        ResultSet chainResultSet = mock(ResultSet.class);
        ResultSet channelResultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT CODIGO, CODIGO_BILLETERA_DIGITAL, MINIMO, MAXIMO, "
                + "NVL(CADENA_FYB, 'N') CADENA_FYB, NVL(CADENA_SANA, 'N') CADENA_SANA, "
                + "NVL(CADENA_OKI, 'N') CADENA_OKI, NVL(CADENA_FR, 'N') CADENA_FR "
                + "FROM TUKUNAFUNC.AD_TIPO_PAGO "
                + "WHERE NVL(ACTIVO, 'N') = 'S'")).thenReturn(chainStatement);
        when(connection.prepareStatement("SELECT "
                + "A.DESCRIPCION AS CANAL, "
                + "A.ACTIVO AS ESTADO, "
                + "B.CODIGO_TIPOPAGO AS ID_BANCO, "
                + "C.CODIGO_BILLETERA_DIGITAL AS ID_PROVEEDOR_PAGO "
                + "FROM TUKUNAFUNC.AD_CANAL A, "
                + "TUKUNAFUNC.AD_CANAL_TIPO_PAGO B, "
                + "TUKUNAFUNC.AD_TIPO_PAGO C "
                + "WHERE A.CODIGO = B.CODIGO_CANAL "
                + "AND B.CODIGO_TIPOPAGO = C.CODIGO "
                + "AND A.ACTIVO = 'S'")).thenReturn(channelStatement);

        when(chainStatement.executeQuery()).thenReturn(chainResultSet);
        when(channelStatement.executeQuery()).thenReturn(channelResultSet);

        when(chainResultSet.next()).thenReturn(true, true, false, true, true, false);
        when(chainResultSet.getString("CODIGO")).thenReturn("0123", "0456", "0123", "0456");
        when(chainResultSet.getInt("CODIGO_BILLETERA_DIGITAL")).thenReturn(235689, 235689, 235689, 235689);
        when(chainResultSet.getBigDecimal("MINIMO"))
                .thenReturn(new BigDecimal("25.00"), (BigDecimal) null, new BigDecimal("25.00"), (BigDecimal) null);
        when(chainResultSet.getBigDecimal("MAXIMO"))
                .thenReturn(new BigDecimal("200.00"), (BigDecimal) null, new BigDecimal("200.00"), (BigDecimal) null);
        when(chainResultSet.getString("CADENA_FYB")).thenReturn("S", "N", "S", "N");
        when(chainResultSet.getString("CADENA_SANA")).thenReturn("N", "N", "N", "N");
        when(chainResultSet.getString("CADENA_OKI")).thenReturn("N", "N", "N", "N");
        when(chainResultSet.getString("CADENA_FR")).thenReturn("N", "N", "N", "N");

        when(channelResultSet.next()).thenReturn(false, false);

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        BanksCatalogService service = new BanksCatalogService(databaseExecutor, 60, 61, 62, 63);
        service.refreshCache();

        assertThat(service.findMinimum(235689, "0123")).contains(new BigDecimal("25.00"));
        assertThat(service.findMinimum(235689, "0456")).isEmpty();
        assertThat(service.findMinimum(235689, "9999")).isEmpty();
        assertThat(service.findMaximum(235689, "0123")).contains(new BigDecimal("200.00"));
        assertThat(service.findMaximum(235689, "0456")).isEmpty();
    }
}
