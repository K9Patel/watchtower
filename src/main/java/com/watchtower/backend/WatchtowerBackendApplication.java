package com.watchtower.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WatchtowerBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(WatchtowerBackendApplication.class, args);
	}
}