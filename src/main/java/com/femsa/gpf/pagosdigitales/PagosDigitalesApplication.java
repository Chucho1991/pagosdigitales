package com.femsa.gpf.pagosdigitales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Aplicación principal de Pagos Digitales.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class PagosDigitalesApplication {

	/**
	 * Punto de entrada de la aplicación.
	 *
	 * @param args argumentos de ejecución.
	 */
	public static void main(String[] args) {
		SpringApplication.run(PagosDigitalesApplication.class, args);
	}

}
