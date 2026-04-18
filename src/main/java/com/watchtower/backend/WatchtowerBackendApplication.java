package com.watchtower.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ServletComponentScan   // AJT Unit 6: enables @WebServlet auto-registration (DiagnosisServlet)
public class WatchtowerBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(WatchtowerBackendApplication.class, args);
	}
}