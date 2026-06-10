package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.api.dto.IssuerCommissionItem;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseConnectionRegistry;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.IssuerCommissionQueryService;

class IssuerCommissionQueryServiceTest {

    private static final String SELECT_BY_ESTABLISHMENT = "SELECT "
            + "CODIGO_ESTABLECIMIENTO, "
            + "NOMBRE, "
            + "MONTO_MINIMO, "
            + "COMISION_FIJA, "
            + "COMISION_VARIABLE "
            + "FROM TRX3.FEMSA_EMISOR_COMISION "
            + "WHERE CODIGO_ESTABLECIMIENTO = ? "
            + "ORDER BY CODIGO_ESTABLECIMIENTO";

    @Test
    void findIssuerCommissionsFiltersByEstablishmentCode() throws Exception {
        DatabaseConnectionRegistry connectionRegistry = mock(DatabaseConnectionRegistry.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connectionRegistry.getDataSource("APPDFM")).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(SELECT_BY_ESTABLISHMENT)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("CODIGO_ESTABLECIMIENTO")).thenReturn(" 001 ");
        when(resultSet.getString("NOMBRE")).thenReturn(" Banco Uno ");
        when(resultSet.getBigDecimal("MONTO_MINIMO")).thenReturn(new BigDecimal("1.25"));
        when(resultSet.getBigDecimal("COMISION_FIJA")).thenReturn(new BigDecimal("0.50"));
        when(resultSet.getBigDecimal("COMISION_VARIABLE")).thenReturn(new BigDecimal("0.10"));

        IssuerCommissionQueryService service = new IssuerCommissionQueryService(connectionRegistry);

        List<IssuerCommissionItem> result = service.findIssuerCommissions(" 001 ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodigo_establecimiento()).isEqualTo("001");
        assertThat(result.get(0).getNombre()).isEqualTo("Banco Uno");
        assertThat(result.get(0).getMonto_minimo()).isEqualByComparingTo("1.25");
        assertThat(result.get(0).getComision_fija()).isEqualByComparingTo("0.50");
        assertThat(result.get(0).getComision_variable()).isEqualByComparingTo("0.10");
        verify(statement).setString(1, "001");
    }
}
