package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

@RestController
@RequestMapping("/callback")
public class OAuthCallbackController {

	private final SoundCloudService soundCloudService;

	public OAuthCallbackController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	@GetMapping
	public Map<String, Object> handleCallback(@RequestParam("code") String code) {
		// Exchange authorization code for access token
		Map<String, String> tokens = soundCloudService.exchangeAuthorizationCode(code);
		String accessToken = tokens.get("access_token");
		String refreshToken = tokens.get("refresh_token");

		// Immediately try to refresh the access token (to prove refresh works)
		String newAccessToken = null;

		if (refreshToken != null) {
			newAccessToken = soundCloudService.refreshAccessToken(refreshToken);
		}

		// Return all info for testing
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("accessToken", accessToken);
		response.put("refreshToken", refreshToken);
		response.put("refreshedAccessToken", newAccessToken);

		return response;
	}
}