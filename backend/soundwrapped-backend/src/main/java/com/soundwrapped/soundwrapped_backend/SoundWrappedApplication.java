package com.soundwrapped.soundwrapped_backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SoundWrappedApplication {
	public static void main(String[] args) {
		//Load .env from backend folder
		Dotenv dotenv = Dotenv.configure().directory("../").filename(".env").load(); //Relative to soundwrapped-backend/src/main/java

		String clientId = dotenv.get("CLIENT_ID");
		String clientSecret = dotenv.get("CLIENT_SECRET");
		String redirectUri = dotenv.get("REDIRECT_URI");

		System.out.println("CLIENT_ID: " + clientId);
		System.out.println("CLIENT_SECRET: " + clientSecret);
		System.out.println("REDIRECT_URI: " + redirectUri);

		SpringApplication.run(SoundWrappedApplication.class, args);
	}
}