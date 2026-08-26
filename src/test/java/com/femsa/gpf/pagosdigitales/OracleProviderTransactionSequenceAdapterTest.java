package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.OracleProviderTransactionSequenceAdapter;

class OracleProviderTransactionSequenceAdapterTest {

    private static final String SELECT_JEP_SEQUENCE =
            "SELECT TUKUNAFUNC.SEQ_JEP_MERCHANT_SALES.NEXTVAL FROM DUAL";
    private static final String SELECT_DEUNA_SEQUENCE =
            "SELECT TUKUNAFUNC.SEQ_DEUNA_MERCHANT_SALES.NEXTVAL FROM DUAL";

    @Test
    void nextJepTransactionIdReadsJepOracleSequence() throws Exception {
        SequenceTestContext context = sequenceContext(SELECT_JEP_SEQUENCE, new BigDecimal("1"));

        BigDecimal result = context.adapter().nextJepTransactionId();

        assertThat(result).isEqualByComparingTo("1");
        verify(context.connection()).prepareStatement(SELECT_JEP_SEQUENCE);
    }

    @Test
    void nextDeunaTransactionIdReadsDeunaOracleSequence() throws Exception {
        SequenceTestContext context = sequenceContext(SELECT_DEUNA_SEQUENCE, new BigDecimal("999999999999999999"));

        BigDecimal result = context.adapter().nextDeunaTransactionId();

        assertThat(result).isEqualByComparingTo("999999999999999999");
        verify(context.connection()).prepareStatement(SELECT_DEUNA_SEQUENCE);
    }

    private SequenceTestContext sequenceContext(String sql, BigDecimal sequenceValue) throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal(1)).thenReturn(sequenceValue);
        when(databaseExecutor.withConnection(any(DatabaseExecutor.ConnectionCallback.class)))
                .thenAnswer(invocation -> {
                    DatabaseExecutor.ConnectionCallback<BigDecimal> callback = invocation.getArgument(0);
                    return callback.execute(connection);
                });

        return new SequenceTestContext(
                new OracleProviderTransactionSequenceAdapter(databaseExecutor), connection);
    }

    private record SequenceTestContext(
            OracleProviderTransactionSequenceAdapter adapter,
            Connection connection) {
    }
}
