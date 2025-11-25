package com.femsa.gpf.pagosdigitales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PagosDigitalesApplication {

	public static void main(String[] args) {
		SpringApplication.run(PagosDigitalesApplication.class, args);
	}

}
