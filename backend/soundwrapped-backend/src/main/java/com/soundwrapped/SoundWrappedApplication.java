package com.soundwrapped;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SoundWrappedApplication {
	public static void main(String[] args) {
		//Load .env from backend folder
		Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").load(); //Relative to soundwrapped-backend/src/main/java
		SpringApplication.run(SoundWrappedApplication.class, args);
	}
}