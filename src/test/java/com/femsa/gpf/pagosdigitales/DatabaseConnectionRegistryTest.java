package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.config.DatabaseConnectionsProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.DatabaseConnectionsProperties.ConnectionProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.persistence.DatabaseConnectionRegistry;
import com.zaxxer.hikari.HikariDataSource;

class DatabaseConnectionRegistryTest {

    @Test
    void registryExposesPrimaryAndNamedDataSources() {
        DatabaseConnectionsProperties properties = new DatabaseConnectionsProperties();
        properties.setPrimaryName("default");
        properties.getConnections().put("default", connection("jdbc:oracle:thin:@localhost:1521:PRS6", "TUKUNAFUNC"));
        properties.getConnections().put("appdfm", connection("jdbc:oracle:thin:@localhost:1521:APPTEST", "TRX3"));

        try (DatabaseConnectionRegistry registry = new DatabaseConnectionRegistry(properties)) {
            DataSource primaryDataSource = registry.getPrimaryDataSource();
            DataSource appdfmDataSource = registry.getDataSource("APPDFM");

            assertThat(registry.getConnectionNames()).containsExactly("default", "appdfm");
            assertThat(primaryDataSource).isInstanceOf(HikariDataSource.class);
            assertThat(appdfmDataSource).isInstanceOf(HikariDataSource.class);
            assertThat(((HikariDataSource) primaryDataSource).getUsername()).isEqualTo("TUKUNAFUNC");
            assertThat(((HikariDataSource) appdfmDataSource).getJdbcUrl()).contains("APPTEST");
        }
    }

    @Test
    void registryRejectsUnknownDataSourceName() {
        DatabaseConnectionsProperties properties = new DatabaseConnectionsProperties();
        properties.getConnections().put("default", connection("jdbc:oracle:thin:@localhost:1521:PRS6", "TUKUNAFUNC"));

        try (DatabaseConnectionRegistry registry = new DatabaseConnectionRegistry(properties)) {
            assertThatThrownBy(() -> registry.getDataSource("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing");
        }
    }

    private ConnectionProperties connection(String url, String username) {
        ConnectionProperties connection = new ConnectionProperties();
        connection.setUrl(url);
        connection.setUsername(username);
        connection.setPassword("secret");
        connection.setDriverClassName("oracle.jdbc.OracleDriver");
        return connection;
    }
}
