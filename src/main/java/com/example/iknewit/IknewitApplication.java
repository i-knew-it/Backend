package com.example.iknewit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IknewitApplication {

	public static void main(String[] args) {
		SpringApplication.run(IknewitApplication.class, args);
	}

}
