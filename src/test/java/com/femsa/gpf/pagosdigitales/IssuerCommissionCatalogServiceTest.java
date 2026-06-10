package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseConnectionRegistry;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.IssuerCommissionCatalogService;

class IssuerCommissionCatalogServiceTest {

    private static final String SELECT_ISSUER_COMMISSIONS = "SELECT "
            + "B.CODIGO_ESTABLECIMIENTO, "
            + "B.DESCRIPCION, "
            + "NVL(D.MONTO_MIN, 0) MONTO_MINIMO, "
            + "NVL(D.COMISION_FIJA, 0) COMISION_FIJA, "
            + "NVL(D.COMISION, 0) COMISION_VARIABLE "
            + "FROM TUKUNAFUNC.AD_TIPO_PAGO B, TUKUNAFUNC.AD_COMISION_TIPOPAGO D "
            + "WHERE B.CODIGO = D.CODIGO_TIPO_PAGO(+) "
            + "AND D.ACTIVO(+) = 'S'";

    @Test
    void refreshCacheLoadsIssuerCommissionsAndMergesIntoAppdfm() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        DatabaseConnectionRegistry connectionRegistry = mock(DatabaseConnectionRegistry.class);
        DataSource appdfmDataSource = mock(DataSource.class);
        Connection sourceConnection = mock(Connection.class);
        Connection targetConnection = mock(Connection.class);
        PreparedStatement selectStatement = mock(PreparedStatement.class);
        PreparedStatement mergeStatement = mock(PreparedStatement.class);
        PreparedStatement targetSelectStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        ResultSet sourceResultSet = mock(ResultSet.class);
        ResultSet targetResultSet = mock(ResultSet.class);

        when(sourceConnection.prepareStatement(SELECT_ISSUER_COMMISSIONS)).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(sourceResultSet);
        when(sourceResultSet.next()).thenReturn(true, true, false);
        when(sourceResultSet.getString("CODIGO_ESTABLECIMIENTO")).thenReturn(" 001 ", "002");
        when(sourceResultSet.getString("DESCRIPCION")).thenReturn(" Banco Uno ", "Banco Dos");
        when(sourceResultSet.getBigDecimal("MONTO_MINIMO"))
                .thenReturn(new BigDecimal("1.250000"), BigDecimal.ZERO);
        when(sourceResultSet.getBigDecimal("COMISION_FIJA"))
                .thenReturn(new BigDecimal("0.500000"), new BigDecimal("0.750000"));
        when(sourceResultSet.getBigDecimal("COMISION_VARIABLE"))
                .thenReturn(new BigDecimal("0.100000"), new BigDecimal("0.200000"));

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(sourceConnection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        when(connectionRegistry.getDataSource("APPDFM")).thenReturn(appdfmDataSource);
        when(appdfmDataSource.getConnection()).thenReturn(targetConnection);
        when(targetConnection.prepareStatement(org.mockito.ArgumentMatchers.startsWith("MERGE INTO")))
                .thenReturn(mergeStatement);
        when(targetConnection.prepareStatement("SELECT CODIGO_ESTABLECIMIENTO FROM FEMSA_EMISOR_COMISION"))
                .thenReturn(targetSelectStatement);
        when(targetSelectStatement.executeQuery()).thenReturn(targetResultSet);
        when(targetResultSet.next()).thenReturn(true, true, true, false);
        when(targetResultSet.getString("CODIGO_ESTABLECIMIENTO")).thenReturn("001", "002", "003");
        when(targetConnection.prepareStatement("DELETE FROM FEMSA_EMISOR_COMISION WHERE CODIGO_ESTABLECIMIENTO = ?"))
                .thenReturn(deleteStatement);

        IssuerCommissionCatalogService service =
                new IssuerCommissionCatalogService(databaseExecutor, connectionRegistry);
        service.refreshCache();

        assertThat(service.findByEstablishmentCode("001"))
                .hasValueSatisfying(commission -> {
                    assertThat(commission.name()).isEqualTo("Banco Uno");
                    assertThat(commission.minimumAmount()).isEqualByComparingTo("1.250000");
                    assertThat(commission.fixedCommission()).isEqualByComparingTo("0.500000");
                    assertThat(commission.variableCommission()).isEqualByComparingTo("0.100000");
                });
        assertThat(service.findByEstablishmentCode("002")).isPresent();

        verify(mergeStatement).setString(1, "001");
        verify(mergeStatement).setString(2, "Banco Uno");
        verify(mergeStatement).setBigDecimal(3, new BigDecimal("1.250000"));
        verify(mergeStatement).setBigDecimal(4, new BigDecimal("0.500000"));
        verify(mergeStatement).setBigDecimal(5, new BigDecimal("0.100000"));
        verify(mergeStatement, times(2)).addBatch();
        verify(mergeStatement).executeBatch();
        verify(deleteStatement).setString(1, "003");
        verify(deleteStatement).addBatch();
        verify(deleteStatement).executeBatch();
    }
}
