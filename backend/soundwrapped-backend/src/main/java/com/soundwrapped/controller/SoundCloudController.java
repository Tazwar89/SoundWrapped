package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundCloudController handles SoundCloud API endpoints with refresh token support and
 * persistent refreshed access tokens.
 * 
 * @author Tazwar Sikder
 */
@RestController
@RequestMapping("/api/soundcloud")
public class SoundCloudController {
	private final SoundCloudService soundCloudService;
	// Simple in-memory storage for access tokens, keyed by refresh token
	// Replaced by Database storage later
	private final Map<String, String> accessTokenStore = new ConcurrentHashMap<>();

	public SoundCloudController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	/**
	 * Gets the current valid access token for a user, refreshing if needed.
	 */
	private String getValidAccessToken(String accessToken, String refreshToken) {
		try {
			//Try a test request to see if access token is valid
			soundCloudService.getUserProfile(accessToken, refreshToken);
			accessTokenStore.put(refreshToken, accessToken); // persist valid token

			return accessToken;
		}

		catch (Exception e) {
			//If expired, refresh
			Map<String, Object> newTokens = soundCloudService.refreshAccessToken(refreshToken);
			String newAccessToken = (String) newTokens.get("access_token");
			accessTokenStore.put(refreshToken, newAccessToken); // persist refreshed token

			return newAccessToken;
		}
	}

	private String extractToken(String bearerToken) {
		return bearerToken.replace("Bearer ", "");
	}

	private String getValidTokenFromHeaders(String bearerToken, String refreshToken) {
		return getValidAccessToken(extractToken(bearerToken), refreshToken);
	}

	@GetMapping("/profile")
	public Map<String, Object> getUserProfile(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserProfile(validToken, refreshToken);
	}

	@GetMapping("/likes")
	public List<Map<String, Object>> getUserLikes(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserLikes(validToken, refreshToken);
	}

	@GetMapping("/playlists")
	public List<Map<String, Object>> getUserPlaylists(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserPlaylists(validToken, refreshToken);
	}

	@GetMapping("/followers")
	public List<Map<String, Object>> getUserFollowers(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserFollowers(validToken, refreshToken);
	}

	@GetMapping("/tracks")
	public List<Map<String, Object>> getUserTracks(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserTracks(validToken, refreshToken);
	}

	@GetMapping("/wrapped/likes")
	public Map<String, Object> getWrappedLikes(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getWrappedLikes(validToken, refreshToken);
	}

	/**
	 * 
	 */
	@GetMapping("/wrapped/full")
	public Map<String, Object> getWrappedSummaryWithRefresh(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getFullWrappedSummary(validToken, refreshToken);
	}

	/**
	 * what is this for?
	 * Optional endpoint: return stored access token for a refresh token
	 */
	@GetMapping("/token")
	public Map<String, String> getStoredAccessToken(
			@RequestHeader("Refresh-Token") String refreshToken) {
		return Map.of("accessToken", accessTokenStore.getOrDefault(refreshToken, ""));
	}
}