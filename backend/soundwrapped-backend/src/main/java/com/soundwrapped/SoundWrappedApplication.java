package com.soundwrapped;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SoundWrappedApplication 
 * 
 * @author Tazwar Sikder
 */

@SpringBootApplication
@EnableScheduling
public class SoundWrappedApplication {
	public static void main(String[] args) {
		//Load .env from backend folder and set as system properties for Spring Boot
		try {
			Dotenv dotenv = Dotenv.configure()
				.directory("./")
				.filename(".env")
				.ignoreIfMissing()
				.load();
			
			// Set each .env variable as a system property so Spring Boot can read it
			// Spring Boot's @Value can read from system properties
			dotenv.entries().forEach(entry -> {
				String key = entry.getKey();
				String value = entry.getValue();
				if (key != null && value != null && !value.trim().isEmpty()) {
					// Set as system property (Spring Boot reads these)
					System.setProperty(key, value);
				}
			});
			
			int loadedCount = (int) dotenv.entries().stream()
				.filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().trim().isEmpty())
				.count();
			
			System.out.println("✓ Loaded .env file with " + loadedCount + " variables");
			// Log which keys were loaded (without values for security)
			dotenv.entries().forEach(entry -> {
				if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
					System.out.println("  - " + entry.getKey() + " = [REDACTED]");
				}
			});
		} catch (Exception e) {
			System.out.println("⚠ No .env file found or error loading it, using application.yml configuration");
			System.out.println("  Error: " + e.getMessage());
		}
		
		SpringApplication.run(SoundWrappedApplication.class, args);
	}
}