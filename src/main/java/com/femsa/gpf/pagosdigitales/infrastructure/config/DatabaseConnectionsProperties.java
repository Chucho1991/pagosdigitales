package com.femsa.gpf.pagosdigitales.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de conexiones JDBC disponibles para la aplicacion.
 */
@Validated
@ConfigurationProperties(prefix = "database")
public class DatabaseConnectionsProperties {

    @NotBlank
    private String primaryName = "default";

    @Valid
    @NotEmpty
    private Map<String, ConnectionProperties> connections = new LinkedHashMap<>();

    /**
     * Obtiene el nombre de la conexion primaria.
     *
     * @return nombre de la conexion primaria
     */
    public String getPrimaryName() {
        return primaryName;
    }

    /**
     * Define el nombre de la conexion primaria.
     *
     * @param primaryName nombre de la conexion primaria
     */
    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    /**
     * Obtiene las conexiones configuradas por nombre.
     *
     * @return conexiones configuradas
     */
    public Map<String, ConnectionProperties> getConnections() {
        return connections;
    }

    /**
     * Define las conexiones configuradas por nombre.
     *
     * @param connections conexiones configuradas
     */
    public void setConnections(Map<String, ConnectionProperties> connections) {
        this.connections = connections;
    }

    /**
     * Propiedades JDBC y de pool para una conexion.
     */
    public static class ConnectionProperties {

        @NotBlank
        private String url;

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        @NotBlank
        private String driverClassName;

        @Valid
        private PoolProperties pool = new PoolProperties();

        /**
         * Obtiene la URL JDBC.
         *
         * @return URL JDBC
         */
        public String getUrl() {
            return url;
        }

        /**
         * Define la URL JDBC.
         *
         * @param url URL JDBC
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * Obtiene el usuario de la conexion.
         *
         * @return usuario de la conexion
         */
        public String getUsername() {
            return username;
        }

        /**
         * Define el usuario de la conexion.
         *
         * @param username usuario de la conexion
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Obtiene la clave de la conexion.
         *
         * @return clave de la conexion
         */
        public String getPassword() {
            return password;
        }

        /**
         * Define la clave de la conexion.
         *
         * @param password clave de la conexion
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Obtiene la clase del driver JDBC.
         *
         * @return clase del driver JDBC
         */
        public String getDriverClassName() {
            return driverClassName;
        }

        /**
         * Define la clase del driver JDBC.
         *
         * @param driverClassName clase del driver JDBC
         */
        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        /**
         * Obtiene las propiedades del pool.
         *
         * @return propiedades del pool
         */
        public PoolProperties getPool() {
            return pool;
        }

        /**
         * Define las propiedades del pool.
         *
         * @param pool propiedades del pool
         */
        public void setPool(PoolProperties pool) {
            this.pool = pool;
        }
    }

    /**
     * Propiedades de pool Hikari para una conexion JDBC.
     */
    public static class PoolProperties {

        @Min(1)
        private int maximumPoolSize = 20;

        @Min(0)
        private int minimumIdle = 5;

        @Min(1)
        private long connectionTimeout = 2000L;

        @Min(1)
        private long idleTimeout = 300000L;

        @Min(1)
        private long maxLifetime = 1800000L;

        /**
         * Obtiene el maximo de conexiones del pool.
         *
         * @return maximo de conexiones
         */
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        /**
         * Define el maximo de conexiones del pool.
         *
         * @param maximumPoolSize maximo de conexiones
         */
        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        /**
         * Obtiene el minimo de conexiones inactivas.
         *
         * @return minimo de conexiones inactivas
         */
        public int getMinimumIdle() {
            return minimumIdle;
        }

        /**
         * Define el minimo de conexiones inactivas.
         *
         * @param minimumIdle minimo de conexiones inactivas
         */
        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        /**
         * Obtiene el timeout para obtener conexion.
         *
         * @return timeout para obtener conexion en ms
         */
        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * Define el timeout para obtener conexion.
         *
         * @param connectionTimeout timeout para obtener conexion en ms
         */
        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /**
         * Obtiene el timeout de inactividad.
         *
         * @return timeout de inactividad en ms
         */
        public long getIdleTimeout() {
            return idleTimeout;
        }

        /**
         * Define el timeout de inactividad.
         *
         * @param idleTimeout timeout de inactividad en ms
         */
        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        /**
         * Obtiene el tiempo maximo de vida de una conexion.
         *
         * @return tiempo maximo de vida en ms
         */
        public long getMaxLifetime() {
            return maxLifetime;
        }

        /**
         * Define el tiempo maximo de vida de una conexion.
         *
         * @param maxLifetime tiempo maximo de vida en ms
         */
        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }
    }
}
