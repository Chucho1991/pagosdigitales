package com.femsa.gpf.pagosdigitales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogRecord;
import com.femsa.gpf.pagosdigitales.infrastructure.logging.IntegrationLogService;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;

class IntegrationLogServiceTest {

    @Test
    void logExternalPersistsProviderSequenceAsBigDecimal() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer consumer = invocation.getArgument(0);
            consumer.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        IntegrationLogService service = new IntegrationLogService(new ObjectMapper(), databaseExecutor);
        BigDecimal sequence = new BigDecimal("9999999999999999999999999999");
        IntegrationLogRecord record = IntegrationLogRecord.builder()
                .requestPayload(java.util.Map.of("internalTransactionReference", sequence.toPlainString()))
                .responsePayload(java.util.Map.of("transactionId", "DEUNA-OP-1"))
                .folio("MERCHANT-1")
                .cpNumber3(sequence)
                .build();

        service.logExternal(record);

        verify(connection).prepareStatement(argThat(sql -> sql.contains("IN_LOGS_WS_EXT")));
        verify(statement).setString(10, "MERCHANT-1");
        verify(statement).setString(17, "DEUNA-OP-1");
        verify(statement).setBigDecimal(21, sequence);
        verify(statement).executeUpdate();
    }

    @Test
    void logInternalPersistsJepNativeIdentifiersInApplicationLog() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer consumer = invocation.getArgument(0);
            consumer.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        IntegrationLogService service = new IntegrationLogService(new ObjectMapper(), databaseExecutor);
        IntegrationLogRecord record = IntegrationLogRecord.builder()
                .requestPayload(java.util.Map.of("idtransaccion", "JEP-OP-1"))
                .folio("JEP-OP-1")
                .build();

        service.logInternal(record);

        verify(connection).prepareStatement(argThat(sql -> sql.contains("IN_LOGS_APP_PAG_DIGIT")));
        verify(statement).setString(10, "JEP-OP-1");
        verify(statement).setString(17, "JEP-OP-1");
        verify(statement).executeUpdate();
    }
}
