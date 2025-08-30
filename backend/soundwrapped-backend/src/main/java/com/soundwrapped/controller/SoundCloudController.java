package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for handling SoundCloud API endpoints.
 * <p>
 * Provides user profile data, likes, playlists, followers, and tracks,
 * along with a personalized "SoundCloud Wrapped" summary.
 * Access tokens are refreshed automatically using refresh tokens, for persistence.
 * </p>
 * 
 * @author Tazwar Sikder
 */
@RestController
@RequestMapping("/api/soundcloud")
public class SoundCloudController {
	private final SoundCloudService soundCloudService;
	// Simple in-memory storage for access tokens, keyed by refresh token
	// TODO: Replace with database-backed token storage for production
	private final Map<String, String> accessTokenStore = new ConcurrentHashMap<String, String>();

	public SoundCloudController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	// =========================
	// User Data Endpoints
	// =========================

	/**
	 * Fetches the authenticated user's SoundCloud profile.
	 * 
	 * @param token        Bearer access token
	 * @param refreshToken Refresh token
	 * @return             User profile as a JSON map
	 */
	@GetMapping("/profile")
	public Map<String, Object> getUserProfile(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserProfile(validToken, refreshToken);
	}

	/**
	 * Fetches the authenticated user's liked tracks.
	 * 
	 */
	@GetMapping("/likes")
	public List<Map<String, Object>> getUserLikes(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserLikes(validToken, refreshToken);
	}

	/**
	 * Fetches the authenticated user's playlists.
	 * 
	 */
	@GetMapping("/playlists")
	public List<Map<String, Object>> getUserPlaylists(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserPlaylists(validToken, refreshToken);
	}

	/**
	 * Fetches the authenticated user's followers.
	 * 
	 */
	@GetMapping("/followers")
	public List<Map<String, Object>> getUserFollowers(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserFollowers(validToken, refreshToken);
	}

	/**
	 * Fetches the authenticated user's uploaded tracks.
	 * 
	 */
	@GetMapping("/tracks")
	public List<Map<String, Object>> getUserTracks(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.getUserTracks(validToken, refreshToken);
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
	public Map<String, Object> getWrappedSummaryWithRefresh(
			@RequestHeader("Authorization") String token,
			@RequestHeader("Refresh-Token") String refreshToken) {
		String validToken = getValidTokenFromHeaders(token, refreshToken);

		return soundCloudService.formattedWrappedSummary(validToken, refreshToken);
	}

	// =========================
	// Token Utilities
	// =========================

	/**
	 * Returns the currently stored access token for the given refresh token.
     * <p>
     * Mainly useful for debugging or testing.
     * </p>
     * 
	 * @param refreshToken Refresh token
	 * @return             JSON object containing the access token
	 */
	@GetMapping("/token")
	public Map<String, String> getStoredAccessToken(
			@RequestHeader("Refresh-Token") String refreshToken) {
		return Map.of("accessToken", accessTokenStore.getOrDefault(refreshToken, ""));
	}

	// =========================
	// Helper Methods
	// =========================

	/**
	 * Returns a valid SoundCloud access token.
	 * <p>
	 * Attempts a test API call with the provided access token. If it is valid, it is
	 * returned and persisted. If the token is expired or invalid, a new token is
	 * obtained using the refresh token.
	 * 
	 * @return Valid access token as a {@code String}
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
			String newAccessToken = soundCloudService.refreshAccessToken(refreshToken);
			accessTokenStore.put(refreshToken, newAccessToken); // persist refreshed token

			return newAccessToken;
		}
	}

	/**
	 * Extracts the raw token value from a "Bearer <token>" string
	 */
	private String extractToken(String bearerToken) {
		return bearerToken.replace("Bearer ", "");
	}

	/**
	 * Combines extraction and validation of the token from headers
	 */
	private String getValidTokenFromHeaders(String bearerToken, String refreshToken) {
		return getValidAccessToken(extractToken(bearerToken), refreshToken);
	}
}