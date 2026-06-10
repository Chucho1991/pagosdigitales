package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import com.femsa.gpf.pagosdigitales.infrastructure.config.DatabaseConnectionsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DatabaseConnectionsProperties.ConnectionProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DatabaseConnectionsProperties.PoolProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Registro de datasources JDBC disponibles por nombre.
 */
public class DatabaseConnectionRegistry implements AutoCloseable {

    private static final String ORACLE_TIMEZONE_AS_REGION = "oracle.jdbc.timezoneAsRegion";

    private final String primaryName;
    private final Map<String, DataSource> dataSources;

    /**
     * Crea el registro de datasources desde propiedades externas.
     *
     * @param properties propiedades de conexiones JDBC
     */
    public DatabaseConnectionRegistry(DatabaseConnectionsProperties properties) {
        this.primaryName = normalize(properties.getPrimaryName());
        this.dataSources = buildDataSources(properties.getConnections());
        if (!dataSources.containsKey(primaryName)) {
            throw new IllegalArgumentException("La conexion primaria no esta configurada: " + properties.getPrimaryName());
        }
    }

    /**
     * Obtiene el datasource primario.
     *
     * @return datasource primario
     */
    public DataSource getPrimaryDataSource() {
        return getDataSource(primaryName);
    }

    /**
     * Obtiene un datasource por nombre.
     *
     * @param name nombre de la conexion
     * @return datasource asociado al nombre
     * @throws IllegalArgumentException si la conexion no existe
     */
    public DataSource getDataSource(String name) {
        DataSource dataSource = dataSources.get(normalize(name));
        if (dataSource == null) {
            throw new IllegalArgumentException("No existe una conexion JDBC configurada con nombre: " + name);
        }
        return dataSource;
    }

    /**
     * Obtiene los nombres de conexiones disponibles.
     *
     * @return nombres de conexiones
     */
    public Set<String> getConnectionNames() {
        return dataSources.keySet();
    }

    /**
     * Cierra los pools JDBC registrados.
     */
    @Override
    public void close() {
        dataSources.values().forEach(dataSource -> {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        });
    }

    private Map<String, DataSource> buildDataSources(Map<String, ConnectionProperties> connections) {
        Map<String, DataSource> configuredDataSources = new LinkedHashMap<>();
        connections.forEach((name, connection) ->
                configuredDataSources.put(normalize(name), buildDataSource(name, connection)));
        return Collections.unmodifiableMap(configuredDataSources);
    }

    private DataSource buildDataSource(String name, ConnectionProperties connection) {
        PoolProperties pool = connection.getPool();
        HikariConfig config = new HikariConfig();
        config.setPoolName("pagosdigitales-" + normalize(name));
        config.setJdbcUrl(connection.getUrl());
        config.setUsername(connection.getUsername());
        config.setPassword(connection.getPassword());
        config.setDriverClassName(connection.getDriverClassName());
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeout());
        config.setIdleTimeout(pool.getIdleTimeout());
        config.setMaxLifetime(pool.getMaxLifetime());
        config.setInitializationFailTimeout(-1L);
        config.addDataSourceProperty(ORACLE_TIMEZONE_AS_REGION, false);
        return new HikariDataSource(config);
    }

    private String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
