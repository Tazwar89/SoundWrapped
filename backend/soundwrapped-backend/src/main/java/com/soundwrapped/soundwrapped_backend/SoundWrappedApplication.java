package com.soundwrapped.soundwrapped_backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SoundWrappedApplication {
	public static void main(String[] args) {
		//Load .env from backend folder
		Dotenv dotenv = Dotenv.configure().directory("../").filename(".env").load(); //Relative to soundwrapped-backend/src/main/java

		System.out.println("CLIENT_ID: " + dotenv.get("CLIENT_ID"));
		System.out.println("CLIENT_SECRET: " + dotenv.get("CLIENT_SECRET"));
		System.out.println("REDIRECT_URI: " + dotenv.get("REDIRECT_URI"));

		SpringApplication.run(SoundWrappedApplication.class, args);
	}
}