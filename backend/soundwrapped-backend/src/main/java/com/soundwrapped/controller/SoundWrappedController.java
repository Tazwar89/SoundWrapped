package com.soundwrapped.controller;

import com.soundwrapped.service.SoundWrappedService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * REST controller for handling SoundCloud API endpoints.
 * <p>
 * Provides user profile data, likes, playlists, followers, and tracks,
 * along with a personalized "SoundCloud Wrapped" summary.
 * Tokens are managed automatically via TokenStore.
 * </p>
 * 
 * @author Tazwar Sikder
 */
@RestController
@RequestMapping("/api/soundcloud")
public class SoundWrappedController {
	private final SoundWrappedService soundWrappedService;

	public SoundWrappedController(SoundWrappedService soundCloudService) {
		this.soundWrappedService = soundCloudService;
	}

	// =========================
	// User Data Endpoints
	// =========================

	@GetMapping("/profile")
	public Map<String, Object> getUserProfile() {
		return soundWrappedService.getUserProfile();
	}

	@GetMapping("/likes")
	public List<Map<String, Object>> getUserLikes() {
		return soundWrappedService.getUserLikes();
	}

	@GetMapping("/playlists")
	public List<Map<String, Object>> getUserPlaylists() {
		return soundWrappedService.getUserPlaylists();
	}

	@GetMapping("/followers")
	public List<Map<String, Object>> getUserFollowers() {
		return soundWrappedService.getUserFollowers();
	}

	@GetMapping("/tracks")
	public List<Map<String, Object>> getUserTracks() {
		return soundWrappedService.getUserTracks();
	}

	// =========================
	// Wrapped Summary
	// =========================

	/**
	 * Generates a "SoundCloud Wrapped"-style summary for the authenticated user.
     * <p>
     * Includes insights such as top artists, top tracks, reposts, and more.
     * </p>
     * 
	 * @param token        Bearer access token
	 * @param refreshToken Refresh token
	 * @return             Map containing summary data and statistics
	 */
	@GetMapping("/wrapped/full")
	public Map<String, Object> getWrappedSummary() {
		return soundWrappedService.formattedWrappedSummary();
	}

	// Debug endpoint to check token status
	@GetMapping("/debug/tokens")
	public Map<String, Object> getTokenStatus() {
		Map<String, Object> status = new HashMap<>();
		status.put("hasAccessToken", soundWrappedService.getTokenStore().getAccessToken() != null);
		status.put("hasRefreshToken", soundWrappedService.getTokenStore().getRefreshToken() != null);
		status.put("accessTokenLength", soundWrappedService.getTokenStore().getAccessToken() != null ? 
			soundWrappedService.getTokenStore().getAccessToken().length() : 0);
		return status;
	}

	// Test OAuth URL generation
	@GetMapping("/debug/oauth-url")
	public Map<String, Object> getOAuthUrl() {
		Map<String, Object> result = new HashMap<>();
		String clientId = "5pRC171gW1jxprhKPRMUJ5mpsCLRfmaM";
		String redirectUri = "http://localhost:8080/callback";
		String scope = "";
		String responseType = "code";
		String authUrl = String.format("https://api.soundcloud.com/connect?client_id=%s&redirect_uri=%s&response_type=%s&scope=%s",
			clientId, redirectUri, responseType, scope);
		result.put("oauthUrl", authUrl);
		result.put("clientId", clientId);
		result.put("redirectUri", redirectUri);
		return result;
	}
}