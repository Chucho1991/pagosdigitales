package com.femsa.gpf.pagosdigitales.infrastructure.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Configuracion de seguridad para operaciones administrativas.
 */
@Configuration
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class SecurityConfig {

    /**
     * Protege el refresco de caches y conserva publicos los contratos existentes.
     *
     * @param http configurador de seguridad HTTP
     * @return cadena de filtros configurada
     * @throws Exception si Spring Security no puede construir la cadena
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/cache/refresh").hasRole("CACHE_ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(withDefaults())
                .build();
    }
}
