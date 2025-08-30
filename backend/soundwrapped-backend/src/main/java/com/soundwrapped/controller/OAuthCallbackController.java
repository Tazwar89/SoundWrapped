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
	private final SoundCloudService soundCloudService;
	private final TokenStore tokenStore;

	public OAuthCallbackController(SoundCloudService soundCloudService, TokenStore tokenStore) {
		this.soundCloudService = soundCloudService;
		this.tokenStore = tokenStore;
	}

	@GetMapping
	public Map<String, Object> handleCallback(@RequestParam("code") String code) {
		//Exchange authorization code and persist tokens internally
		soundCloudService.exchangeAuthorizationCode(code);

		//Return stored tokens for immediate verification/testing
		return Map.of(
				"accessToken", tokenStore.getAccessToken(),
				"refreshToken", tokenStore.getRefreshToken());
	}
}