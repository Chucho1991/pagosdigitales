package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

class PaymentRegistryServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void findPaymentStatusReadsJepStateByOperationAndProvider() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getTimestamp("FECHA_REGISTRO"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 2, 19, 9, 52, 3)));
        when(resultSet.getTimestamp("FECHA_AUTORIZACION_PROV"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 19, 10, 36, 57)));
        when(resultSet.getString("FOLIO")).thenReturn("8");
        when(resultSet.getString("ID_OPERACION_EXTERNO")).thenReturn("8");
        when(resultSet.getString("ID_INTERNO_VENTA")).thenReturn("8");
        when(resultSet.getString("NO_REFERENCIA")).thenReturn("8");
        when(resultSet.getString("COD_ESTADO_PAGO")).thenReturn("PAGADO");
        when(resultSet.getString("CP_VAR1")).thenReturn("JEP_CONFIRMATION_OK");

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(connection);
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionCallback.class));

        PaymentRegistryService service = new PaymentRegistryService(databaseExecutor);

        var payment = service.findPaymentStatus("8", 300001);

        assertThat(payment).isPresent();
        assertThat(payment.orElseThrow().paymentStatus()).isEqualTo("PAGADO");
        assertThat(payment.orElseThrow().statusDetail()).isEqualTo("JEP_CONFIRMATION_OK");
        verify(statement).setString(1, "8");
        verify(statement).setString(2, "300001");
    }
}
