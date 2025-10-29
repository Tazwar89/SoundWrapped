package com.soundwrapped.controller;

import com.soundwrapped.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Map;

/**
 * Handles the OAuth callback from SoundCloud.
 * Exchanges the authorization code for access and refresh tokens
 * and stores them in TokenStore for automatic use.
 */
@RestController
@RequestMapping("/callback")
public class OAuthCallbackController {
	private final SoundWrappedService soundWrappedService;
	private final TokenStore tokenStore;

	public OAuthCallbackController(SoundWrappedService soundWrappedService, TokenStore tokenStore) {
		this.soundWrappedService = soundWrappedService;
		this.tokenStore = tokenStore;
	}

	@GetMapping
	public ResponseEntity<Void> handleCallback(@RequestParam("code") String code) {
		try {
			//Exchange authorization code and persist tokens internally
			Map<String, Object> tokens = soundWrappedService.exchangeAuthorizationCode(code);

			String accessToken = (String) tokens.get("access_token");
			String refreshToken = (String) tokens.get("refresh_token");

			//Persist tokens in database via TokenStore
			tokenStore.saveTokens(accessToken, refreshToken);

			//Redirect to frontend with success message
			return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create("http://localhost:3000?auth=success"))
				.build();
		} catch (Exception e) {
			//Redirect to frontend with error message
			return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create("http://localhost:3000?auth=error"))
				.build();
		}
	}
}