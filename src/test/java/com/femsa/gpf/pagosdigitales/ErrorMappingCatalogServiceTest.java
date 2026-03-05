package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInnerDetail;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseExecutor;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.ErrorMappingCatalogService;

class ErrorMappingCatalogServiceTest {

    @Test
    void mapProviderErrorUsesSpanishAndFallsBackToEnglish() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT HTTP_STATUS, ERROR_CATEGORY, "
                + "INNER_DETAILS_FIELD_MESSAGE, INNER_DETAILS_FIELD_MESSAGE_ES, CURRENT_ERROR_CODE, "
                + "CURRENT_ERROR_MESSAGE, CURRENT_ERROR_MESSAGE_ES "
                + "FROM TUKUNAFUNC.AD_MAPEO_ERRORES")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);

        when(resultSet.getObject("HTTP_STATUS")).thenReturn(400, 500);
        when(resultSet.getString("ERROR_CATEGORY")).thenReturn("INVALID_REQUEST_ERROR", "API_ERROR");
        when(resultSet.getString("INNER_DETAILS_FIELD_MESSAGE"))
                .thenReturn("The country code is required.", "An error occurred when performing a SendEmail.");
        when(resultSet.getString("INNER_DETAILS_FIELD_MESSAGE_ES"))
                .thenReturn("El codigo de pais es requerido.", null);
        when(resultSet.getObject("CURRENT_ERROR_CODE")).thenReturn(21517, 21514);
        when(resultSet.getString("CURRENT_ERROR_MESSAGE"))
                .thenReturn("Transaction amount is required.", "An error occurred when performing a SendEmail.");
        when(resultSet.getString("CURRENT_ERROR_MESSAGE_ES"))
                .thenReturn("El monto de la transaccion es requerido.", null);

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        ErrorMappingCatalogService service = new ErrorMappingCatalogService(databaseExecutor);
        service.refreshCache();

        ErrorInfo inputSpanish = new ErrorInfo();
        inputSpanish.setHttp_code(400);
        inputSpanish.setCode("21517");
        inputSpanish.setCategory("INVALID_REQUEST_ERROR");
        inputSpanish.setMessage("Transaction amount is required.");
        ErrorInnerDetail inputInner = new ErrorInnerDetail();
        inputInner.setField("country_code");
        inputInner.setField_message("The country code is required.");
        inputSpanish.setInner_details(List.of(inputInner));

        ErrorInfo mappedSpanish = service.mapProviderError(inputSpanish);
        assertThat(mappedSpanish.getHttp_code()).isEqualTo(400);
        assertThat(mappedSpanish.getCode()).isEqualTo("21517");
        assertThat(mappedSpanish.getCategory()).isEqualTo("INVALID_REQUEST_ERROR");
        assertThat(mappedSpanish.getMessage()).isEqualTo("El monto de la transaccion es requerido.");
        assertThat(mappedSpanish.getInner_details()).hasSize(1);
        assertThat(mappedSpanish.getInner_details().get(0).getField_message()).isEqualTo("El codigo de pais es requerido.");

        ErrorInfo inputEnglish = new ErrorInfo();
        inputEnglish.setHttp_code(500);
        inputEnglish.setCode("21514");
        inputEnglish.setCategory("API_ERROR");
        inputEnglish.setMessage("An error occurred when performing a SendEmail.");
        ErrorInnerDetail englishInner = new ErrorInnerDetail();
        englishInner.setField("operation");
        englishInner.setField_message("An error occurred when performing a SendEmail.");
        inputEnglish.setInner_details(List.of(englishInner));

        ErrorInfo mappedEnglish = service.mapProviderError(inputEnglish);
        assertThat(mappedEnglish.getMessage()).isEqualTo("An error occurred when performing a SendEmail.");
        assertThat(mappedEnglish.getInner_details().get(0).getField_message())
                .isEqualTo("An error occurred when performing a SendEmail.");
    }

    @Test
    void mapProviderErrorResolvesByCategoryAndMessageWhenCodeIsNotNumeric() throws Exception {
        DatabaseExecutor databaseExecutor = mock(DatabaseExecutor.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT HTTP_STATUS, ERROR_CATEGORY, "
                + "INNER_DETAILS_FIELD_MESSAGE, INNER_DETAILS_FIELD_MESSAGE_ES, CURRENT_ERROR_CODE, "
                + "CURRENT_ERROR_MESSAGE, CURRENT_ERROR_MESSAGE_ES "
                + "FROM TUKUNAFUNC.AD_MAPEO_ERRORES")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);

        when(resultSet.getObject("HTTP_STATUS")).thenReturn(422);
        when(resultSet.getString("ERROR_CATEGORY")).thenReturn("VALIDATION_ERROR");
        when(resultSet.getString("INNER_DETAILS_FIELD_MESSAGE")).thenReturn("Token Expired");
        when(resultSet.getString("INNER_DETAILS_FIELD_MESSAGE_ES")).thenReturn("Token expirado.");
        when(resultSet.getObject("CURRENT_ERROR_CODE")).thenReturn(21603);
        when(resultSet.getString("CURRENT_ERROR_MESSAGE")).thenReturn("Token Expired");
        when(resultSet.getString("CURRENT_ERROR_MESSAGE_ES")).thenReturn("Token expirado.");

        doAnswer(invocation -> {
            DatabaseExecutor.ConnectionConsumer callback = invocation.getArgument(0);
            callback.execute(connection);
            return null;
        }).when(databaseExecutor).withConnection(any(DatabaseExecutor.ConnectionConsumer.class));

        ErrorMappingCatalogService service = new ErrorMappingCatalogService(databaseExecutor);
        service.refreshCache();

        ErrorInfo input = new ErrorInfo();
        input.setHttp_code(422);
        input.setCode("INVALID_REQUEST");
        input.setCategory("VALIDATION_ERROR");
        input.setMessage("Token Expired");
        input.setInner_details(null);

        ErrorInfo mapped = service.mapProviderError(input);
        assertThat(mapped.getHttp_code()).isEqualTo(422);
        assertThat(mapped.getCode()).isEqualTo("21603");
        assertThat(mapped.getMessage()).isEqualTo("Token expirado.");
    }
}
