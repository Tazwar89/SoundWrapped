package com.soundwrapped.controller;

import com.soundwrapped.service.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
	public Map<String, Object> handleCallback(@RequestParam("code") String code) {
		//Exchange authorization code and persist tokens internally
		Map<String, Object> tokens = soundWrappedService.exchangeAuthorizationCode(code);

		String accessToken = (String) tokens.get("access_token");
		String refreshToken = (String) tokens.get("refresh_token");

		//Persist tokens in database via TokenStore
		tokenStore.saveTokens(accessToken, refreshToken);

		//Return stored tokens for immediate verification/testing
		return Map.of(
				"accessToken", tokenStore.getAccessToken(),
				"refreshToken", tokenStore.getRefreshToken());
	}
}