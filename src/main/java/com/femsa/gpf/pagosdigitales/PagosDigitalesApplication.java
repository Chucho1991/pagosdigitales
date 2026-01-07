package com.femsa.gpf.pagosdigitales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto de entrada de la aplicacion Pagos Digitales.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class PagosDigitalesApplication {

	/**
	 * Inicia el contexto de Spring Boot.
	 *
	 * @param args argumentos de linea de comandos
	 */
	public static void main(String[] args) {
		SpringApplication.run(PagosDigitalesApplication.class, args);
	}

}
