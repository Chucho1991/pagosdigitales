package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.api.dto.MerchantEvent;
import com.femsa.gpf.pagosdigitales.api.dto.MerchantEventsRequest;
import com.femsa.gpf.pagosdigitales.api.dto.JepConfirmationRequest;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentAmount;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperation;
import com.femsa.gpf.pagosdigitales.api.dto.PaymentOperationActivity;
import com.femsa.gpf.pagosdigitales.domain.model.GeneratedPayment;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.PaymentRegistryService;

class PaymentRegistryServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void saveInsertsGeneratedPaymentWithOperationId() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.prepareStatement(argThat(sql -> sql.contains("INSERT INTO TUKUNAFUNC.IN_REGISTRO_PAGOS"))))
                .thenReturn(statement);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer consumer = invocation.getArgument(0);
            consumer.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        PaymentRegistryService service = new PaymentRegistryService(databaseExecutor);
        service.save(new GeneratedPayment(1, 148, "FYBECA", 90,
                LocalDateTime.of(2026, 8, 24, 12, 0), "WEB", 300001,
                "SALE-1", "OP-1", "SALE-1", new BigDecimal("3.93"), "USD"));

        verify(statement).setString(9, "OP-1");
        verify(statement).setString(10, "SALE-1");
        verify(statement).setBigDecimal(11, new BigDecimal("3.93"));
        verify(statement).setString(12, "USD");
        verify(statement).setString(13, "101");
        verify(statement).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Test
    void registerMerchantEventsUpdatesExistingOperationWithoutInsertingDuplicate() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).startsWith("UPDATE") ? update : insert);
        when(update.executeUpdate()).thenReturn(1);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer consumer = invocation.getArgument(0);
            consumer.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        PaymentRegistryService service = new PaymentRegistryService(databaseExecutor);
        service.registerMerchantEvents(merchantEventsRequest(), 0);

        verify(update).setString(10, "101");
        verify(update).setString(13, "OP-1");
        verify(update).executeUpdate();
        verify(insert, never()).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Test
    void registerMerchantEventsInsertsWhenOperationDoesNotExist() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).startsWith("UPDATE") ? update : insert);
        when(update.executeUpdate()).thenReturn(0);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer consumer = invocation.getArgument(0);
            consumer.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        PaymentRegistryService service = new PaymentRegistryService(databaseExecutor);
        service.registerMerchantEvents(merchantEventsRequest(), 0);

        verify(insert).setString(9, "OP-1");
        verify(insert).setString(11, "101");
        verify(insert).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateFromJepConfirmationStoresPaidStatusAndReferences() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(connection);
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionCallback.class));

        JepConfirmationRequest request = new JepConfirmationRequest();
        request.setIdtransaccion("OP-1");
        request.setNummensaje("REF-1");
        request.setEstado("PAGADO");

        boolean updated = new PaymentRegistryService(databaseExecutor).updateFromJepConfirmation(request);

        assertThat(updated).isTrue();
        verify(statement).setString(2, "REF-1");
        verify(statement).setString(3, "REF-1");
        verify(statement).setString(4, "102");
        verify(statement).setString(7, "OP-1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void synchronizeDeunaPaymentStatusStoresNormalizedPaidState() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(connection);
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionCallback.class));

        PaymentAmount amount = new PaymentAmount();
        amount.setValue(new BigDecimal("3.93"));
        amount.setCurrency_code("USD");
        PaymentOperationActivity activity = new PaymentOperationActivity();
        activity.setCreation_datetime("2026-08-26T16:40:39");
        activity.setStatus_code("102");
        PaymentOperation operation = new PaymentOperation();
        operation.setOperation_id("DEUNA-OP-1");
        operation.setPayment_amount(amount);
        operation.setPayment_reference_number("REF-1");
        operation.setOperation_activities(List.of(activity));

        boolean updated = new PaymentRegistryService(databaseExecutor)
                .synchronizeDeunaPaymentStatus(operation, 300002);

        assertThat(updated).isTrue();
        verify(statement).setString(2, "REF-1");
        verify(statement).setString(8, "102");
        verify(statement).setString(10, "DEUNA-OP-1");
        verify(statement).setString(11, "300002");
    }

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
        when(resultSet.getString("COD_ESTADO_PAGO")).thenReturn("102");
        when(resultSet.getString("CP_VAR1")).thenReturn("JEP_CONFIRMATION_OK");

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(connection);
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionCallback.class));

        PaymentRegistryService service = new PaymentRegistryService(databaseExecutor);

        var payment = service.findPaymentStatus("8", 300001);

        assertThat(payment).isPresent();
        assertThat(payment.orElseThrow().paymentStatus()).isEqualTo("102");
        assertThat(payment.orElseThrow().statusDetail()).isEqualTo("JEP_CONFIRMATION_OK");
        verify(statement).setString(1, "8");
        verify(statement).setString(2, "300001");
    }

    private MerchantEventsRequest merchantEventsRequest() {
        MerchantEvent event = new MerchantEvent();
        event.setCreation_datetime("2026-08-24T12:00:00");
        event.setOperation_id("OP-1");
        event.setMerchant_sales_id("SALE-1");
        event.setOperation_status("101");

        MerchantEventsRequest request = new MerchantEventsRequest();
        request.setChain(1);
        request.setStore(148);
        request.setStore_name("FYBECA");
        request.setPos(90);
        request.setChannel_POS("WEB");
        request.setPayment_provider_code(300001);
        request.setMerchant_events(List.of(event));
        return request;
    }
}
