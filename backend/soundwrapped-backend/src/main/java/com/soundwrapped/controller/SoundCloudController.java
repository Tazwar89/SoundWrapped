package com.soundwrapped.controller;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
public class SoundCloudController {
	private final String clientId, clientSecret, redirectUri;

	public SoundCloudController() {
		//Load environment variables from .env
		Dotenv dotenv = Dotenv.configure().directory("./").filename(".env").load();

		clientId = dotenv.get("CLIENT_ID");
		clientSecret = dotenv.get("CLIENT_SECRET");
		redirectUri = dotenv.get("REDIRECT_URI");
	}

	@GetMapping("/test-credentials")
	public ResponseEntity<String> testCredential() {
		//Return the fact that environment variables were loaded
		return ResponseEntity.ok("Loaded CLIENT_ID: " + clientId + ", CLIENT_SECRET: " + clientSecret + " and REDIRECT_URI: " + redirectUri);
	}
}