package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/**
 * Componente centralizado para administrar conexiones JDBC.
 */
@Component
public class DatabaseExecutor {

    private final DataSource dataSource;

    /**
     * Crea el ejecutor con el datasource de la aplicacion.
     *
     * @param dataSource datasource JDBC
     */
    public DatabaseExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Ejecuta una operacion usando una conexion gestionada globalmente.
     *
     * @param <T> tipo de retorno
     * @param callback logica a ejecutar con la conexion abierta
     * @return resultado de la operacion
     * @throws Exception cuando ocurre un error JDBC o de negocio
     */
    public <T> T withConnection(ConnectionCallback<T> callback) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return callback.execute(connection);
        }
    }

    /**
     * Ejecuta una operacion sin retorno usando una conexion gestionada globalmente.
     *
     * @param callback logica a ejecutar con la conexion abierta
     * @throws Exception cuando ocurre un error JDBC o de negocio
     */
    public void withConnection(ConnectionConsumer callback) throws Exception {
        withConnection(connection -> {
            callback.execute(connection);
            return null;
        });
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T execute(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface ConnectionConsumer {
        void execute(Connection connection) throws Exception;
    }
}
