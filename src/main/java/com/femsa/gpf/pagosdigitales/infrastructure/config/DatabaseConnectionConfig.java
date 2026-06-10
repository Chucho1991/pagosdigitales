package com.femsa.gpf.pagosdigitales.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseConnectionRegistry;

/**
 * Configura los datasources JDBC nombrados de la aplicacion.
 */
@Configuration
@EnableConfigurationProperties(DatabaseConnectionsProperties.class)
public class DatabaseConnectionConfig {

    /**
     * Construye el registro central de conexiones JDBC.
     *
     * @param properties propiedades de conexiones JDBC
     * @return registro de conexiones JDBC
     */
    @Bean
    public DatabaseConnectionRegistry databaseConnectionRegistry(DatabaseConnectionsProperties properties) {
        return new DatabaseConnectionRegistry(properties);
    }

    /**
     * Expone el datasource primario para compatibilidad con Spring y servicios existentes.
     *
     * @param registry registro de conexiones JDBC
     * @return datasource primario
     */
    @Bean(name = "dataSource", destroyMethod = "")
    @Primary
    public DataSource dataSource(DatabaseConnectionRegistry registry) {
        return registry.getPrimaryDataSource();
    }
}
