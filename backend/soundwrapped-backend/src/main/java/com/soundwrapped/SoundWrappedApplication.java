package com.soundwrapped;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SoundWrappedApplication 
 * 
 * @author Tazwar Sikder
 */

@SpringBootApplication
public class SoundWrappedApplication {
	public static void main(String[] args) {
		//Load .env from backend folder (optional)
		try {
			Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").load();
		} catch (Exception e) {
			System.out.println("No .env file found, using application.yml configuration");
		}
		SpringApplication.run(SoundWrappedApplication.class, args);
	}
}