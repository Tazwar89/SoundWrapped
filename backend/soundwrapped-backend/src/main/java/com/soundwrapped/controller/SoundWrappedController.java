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
		try {
			return soundWrappedService.getUserProfile();
		} catch (Exception e) {
			System.out.println("Error fetching profile: " + e.getMessage());
			Map<String, Object> errorProfile = new HashMap<>();
			errorProfile.put("error", "Unable to fetch profile data");
			errorProfile.put("message", e.getMessage());
			return errorProfile;
		}
	}

	@GetMapping("/likes")
	public List<Map<String, Object>> getUserLikes() {
		return soundWrappedService.getUserLikes();
	}

	@GetMapping("/playlists")
	public List<Map<String, Object>> getUserPlaylists() {
		try {
			return soundWrappedService.getUserPlaylists();
		} catch (Exception e) {
			System.out.println("Error fetching playlists: " + e.getMessage());
			return new ArrayList<>();
		}
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
		try {
			return soundWrappedService.formattedWrappedSummary();
		} catch (Exception e) {
			System.out.println("Error fetching wrapped summary: " + e.getMessage());
			// Return minimal wrapped data based on profile only
			try {
				Map<String, Object> profile = soundWrappedService.getUserProfile();
				Map<String, Object> minimalWrapped = new HashMap<>();
				Map<String, Object> wrappedProfile = new HashMap<>();
				wrappedProfile.put("username", profile.get("username"));
				wrappedProfile.put("followers", profile.get("followers_count"));
				wrappedProfile.put("tracksUploaded", profile.getOrDefault("track_count", 0));
				wrappedProfile.put("playlistsCreated", profile.getOrDefault("playlist_count", 0));
				wrappedProfile.put("accountAgeYears", 0);
				minimalWrapped.put("profile", wrappedProfile);
				minimalWrapped.put("topTracks", new ArrayList<>());
				minimalWrapped.put("topArtists", new ArrayList<>());
				minimalWrapped.put("topRepostedTracks", new ArrayList<>());
				Map<String, Object> stats = new HashMap<>();
				stats.put("totalListeningHours", 0);
				stats.put("likesGiven", profile.getOrDefault("public_favorites_count", 0));
				stats.put("tracksUploaded", profile.getOrDefault("track_count", 0));
				stats.put("commentsPosted", 0);
				stats.put("booksYouCouldHaveRead", 0);
				minimalWrapped.put("stats", stats);
				minimalWrapped.put("funFact", "Unable to load full data - SoundCloud API rate limited");
				minimalWrapped.put("peakYear", "");
				minimalWrapped.put("globalTasteComparison", "");
				minimalWrapped.put("stories", new ArrayList<>());
				return minimalWrapped;
			} catch (Exception profileError) {
				// If even profile fails, return empty structure
				Map<String, Object> emptyWrapped = new HashMap<>();
				Map<String, Object> emptyProfile = new HashMap<>();
				emptyProfile.put("username", "Unknown");
				emptyProfile.put("accountAgeYears", 0);
				emptyProfile.put("followers", 0);
				emptyProfile.put("tracksUploaded", 0);
				emptyProfile.put("playlistsCreated", 0);
				emptyWrapped.put("profile", emptyProfile);
				emptyWrapped.put("topTracks", new ArrayList<>());
				emptyWrapped.put("topArtists", new ArrayList<>());
				emptyWrapped.put("topRepostedTracks", new ArrayList<>());
				Map<String, Object> emptyStats = new HashMap<>();
				emptyStats.put("totalListeningHours", 0);
				emptyStats.put("likesGiven", 0);
				emptyStats.put("tracksUploaded", 0);
				emptyStats.put("commentsPosted", 0);
				emptyStats.put("booksYouCouldHaveRead", 0);
				emptyWrapped.put("stats", emptyStats);
				emptyWrapped.put("funFact", "Unable to load data - please try again later");
				emptyWrapped.put("peakYear", "");
				emptyWrapped.put("globalTasteComparison", "");
				emptyWrapped.put("stories", new ArrayList<>());
				return emptyWrapped;
			}
		}
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