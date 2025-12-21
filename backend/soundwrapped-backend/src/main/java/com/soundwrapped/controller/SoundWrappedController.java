package com.soundwrapped.controller;

import com.soundwrapped.service.SoundWrappedService;
import com.soundwrapped.service.AnalyticsService;
import com.soundwrapped.service.MusicDoppelgangerService;
import com.soundwrapped.service.ArtistAnalyticsService;
import com.soundwrapped.service.MusicTasteMapService;
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
	private final AnalyticsService analyticsService;
	private final MusicDoppelgangerService musicDoppelgangerService;
	private final ArtistAnalyticsService artistAnalyticsService;
	private final MusicTasteMapService musicTasteMapService;

	public SoundWrappedController(
			SoundWrappedService soundCloudService,
			AnalyticsService analyticsService,
			MusicDoppelgangerService musicDoppelgangerService,
			ArtistAnalyticsService artistAnalyticsService,
			MusicTasteMapService musicTasteMapService) {
		this.soundWrappedService = soundCloudService;
		this.analyticsService = analyticsService;
		this.musicDoppelgangerService = musicDoppelgangerService;
		this.artistAnalyticsService = artistAnalyticsService;
		this.musicTasteMapService = musicTasteMapService;
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
		try {
			// Get user profile to extract userId
			Map<String, Object> profile = soundWrappedService.getUserProfile();
			String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
			
			// Get top tracks based on user's tracked plays
			return soundWrappedService.getUserTopTracksByPlays(userId, 50);
		} catch (Exception e) {
			System.out.println("Error fetching tracks: " + e.getMessage());
			return new ArrayList<>();
		}
	}

	@GetMapping("/popular/tracks")
	public List<Map<String, Object>> getPopularTracks(@RequestParam(defaultValue = "4") int limit) {
		try {
			List<Map<String, Object>> result = soundWrappedService.getPopularTracks(limit);
			System.out.println("Controller: Popular tracks result size: " + result.size());
			if (result.isEmpty()) {
				System.err.println("Controller: Popular tracks list is empty!");
			}
			return result;
		} catch (Exception e) {
			System.err.println("Error fetching popular tracks: " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	@GetMapping("/featured/track")
	public Map<String, Object> getFeaturedTrack() {
		try {
			Map<String, Object> result = soundWrappedService.getFeaturedTrack();
			System.out.println("Controller: Featured track result size: " + result.size());
			if (result.isEmpty()) {
				System.err.println("Controller: Featured track is empty!");
			}
			return result;
		} catch (Exception e) {
			System.err.println("Error fetching featured track: " + e.getMessage());
			e.printStackTrace();
			return new HashMap<>();
		}
	}

	@GetMapping("/featured/artist")
	public Map<String, Object> getFeaturedArtist(@RequestParam(required = false) Boolean forceRefresh) {
		System.out.println("========================================");
		System.out.println("Controller: /featured/artist endpoint called" + (forceRefresh != null && forceRefresh ? " (force refresh)" : ""));
		System.out.println("========================================");
		try {
			// Pass forceRefresh to service method
			boolean shouldForceRefresh = forceRefresh != null && forceRefresh;
			Map<String, Object> result = soundWrappedService.getFeaturedArtist(shouldForceRefresh);
			System.out.println("Controller: Service returned result with keys: " + result.keySet());
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> tracks = (List<Map<String, Object>>) result.get("tracks");
			System.out.println("Controller: Featured artist response - username: " + result.get("username") + ", tracks count: " + (tracks != null ? tracks.size() : 0));
			if (tracks == null || tracks.isEmpty()) {
				System.err.println("Controller: WARNING - Featured artist has no tracks! Result keys: " + result.keySet());
				System.err.println("Controller: Attempting to manually fetch tracks...");
				// Try to manually fetch tracks if they're missing
				String artistPermalink = (String) result.getOrDefault("permalink", result.get("username"));
				if (artistPermalink != null && !artistPermalink.toString().isEmpty()) {
					System.out.println("Controller: Manually fetching tracks for: " + artistPermalink);
					// We can't call private methods from controller, so we'll just log
					System.err.println("Controller: Cannot manually fetch - tracks should have been included by service");
				}
			} else {
				System.out.println("Controller: SUCCESS - Featured artist has " + tracks.size() + " tracks");
			}
			System.out.println("Controller: Returning result with tracks: " + (tracks != null ? tracks.size() : 0));
			return result;
		} catch (Exception e) {
			System.err.println("Error fetching featured artist: " + e.getMessage());
			e.printStackTrace();
			return new HashMap<>();
		}
	}

	@GetMapping("/featured/genre")
	public Map<String, Object> getFeaturedGenre() {
		System.out.println("Controller: /featured/genre endpoint called");
		try {
			return soundWrappedService.getFeaturedGenreWithTracks();
		} catch (Exception e) {
			System.err.println("Error fetching featured genre: " + e.getMessage());
			e.printStackTrace();
			// Return default genre with tracks even on error
			Map<String, Object> result = new HashMap<>();
			result.put("genre", "electronic");
			result.put("description", "Electronic music encompasses a wide range of genres that primarily use electronic instruments and technology.");
			result.put("tracks", new ArrayList<>());
			return result;
		}
	}

	/**
	 * Clear the daily cache for featured content (artist, genre, song).
	 * This forces regeneration of descriptions with the latest API integrations.
	 */
	@PostMapping("/featured/clear-cache")
	public Map<String, Object> clearFeaturedCache() {
		Map<String, Object> result = new HashMap<>();
		try {
			soundWrappedService.clearFeaturedCache();
			result.put("success", true);
			result.put("message", "Featured content cache cleared. New descriptions will be generated with latest API integrations.");
			return result;
		} catch (Exception e) {
			System.err.println("Error clearing featured cache: " + e.getMessage());
			e.printStackTrace();
			result.put("success", false);
			result.put("message", "Failed to clear cache: " + e.getMessage());
			return result;
		}
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

	/**
	 * Get combined dashboard analytics (API data + tracked activity)
	 */
	@GetMapping("/dashboard/analytics")
	public Map<String, Object> getDashboardAnalytics() {
		try {
			Map<String, Object> profile = soundWrappedService.getUserProfile();
			String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
			List<Map<String, Object>> tracks = soundWrappedService.getUserTracks();
			
			return analyticsService.getDashboardAnalytics(userId, profile, tracks);
		} catch (Exception e) {
			System.out.println("Error fetching dashboard analytics: " + e.getMessage());
			Map<String, Object> error = new HashMap<>();
			error.put("error", "Unable to fetch analytics");
			error.put("message", e.getMessage());
			return error;
		}
	}

	/**
	 * Get Music Doppelgänger (taste matching with followed users)
	 * This endpoint can be called separately as it may take time due to API calls.
	 */
	@GetMapping("/music-doppelganger")
	public Map<String, Object> getMusicDoppelganger() {
		try {
			return musicDoppelgangerService.findMusicDoppelganger();
		} catch (Exception e) {
			System.out.println("Error finding Music Doppelgänger: " + e.getMessage());
			Map<String, Object> error = new HashMap<>();
			error.put("found", false);
			error.put("message", "Error analyzing taste similarity: " + e.getMessage());
			return error;
		}
	}

	/**
	 * Get artist analytics (for users who upload tracks)
	 */
	@GetMapping("/artist/analytics")
	public Map<String, Object> getArtistAnalytics() {
		try {
			Map<String, Object> profile = soundWrappedService.getUserProfile();
			String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
			return artistAnalyticsService.getArtistAnalytics(userId);
		} catch (Exception e) {
			System.out.println("Error fetching artist analytics: " + e.getMessage());
			Map<String, Object> error = new HashMap<>();
			error.put("error", "Unable to fetch artist analytics");
			error.put("message", e.getMessage());
			return error;
		}
	}

	/**
	 * Get artist recommendations based on a track
	 */
	@GetMapping("/artist/recommendations")
	public List<Map<String, Object>> getArtistRecommendations(@RequestParam String trackId) {
		try {
			return artistAnalyticsService.getArtistRecommendations(trackId);
		} catch (Exception e) {
			System.out.println("Error fetching artist recommendations: " + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Get music taste map data showing cities with similar listeners
	 */
	@GetMapping("/music-taste-map")
	public List<Map<String, Object>> getMusicTasteMap() {
		try {
			return musicTasteMapService.getMusicTasteMap();
		} catch (Exception e) {
			System.out.println("Error fetching music taste map: " + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Get recent activity (likes, uploads, follows) from SoundCloud
	 */
	@GetMapping("/recent-activity")
	public List<Map<String, Object>> getRecentActivity(@RequestParam(defaultValue = "10") int limit) {
		try {
			return soundWrappedService.getRecentActivity(limit);
		} catch (Exception e) {
			System.err.println("Error fetching recent activity: " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	// Debug endpoint to test SoundCloud API connection
	@GetMapping("/debug/test-api")
	public Map<String, Object> testSoundCloudAPI() {
		try {
			// Test basic API call - use reflection to get clientId since it's private
			java.lang.reflect.Field clientIdField = soundWrappedService.getClass().getDeclaredField("clientId");
			clientIdField.setAccessible(true);
			String clientId = (String) clientIdField.get(soundWrappedService);
			
			String testUrl = "https://api.soundcloud.com/tracks?client_id=" + clientId + "&limit=1";
			org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
			org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
			headers.set("User-Agent", "SoundWrapped/1.0");
			headers.set("Accept", "application/json");
			org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<String>(headers);
			
			org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>> typeRef = 
				new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>(){};
			org.springframework.http.ResponseEntity<List<Map<String, Object>>> response = 
				restTemplate.exchange(testUrl, org.springframework.http.HttpMethod.GET, request, typeRef);
			
			Map<String, Object> result = new HashMap<>();
			result.put("status", "success");
			result.put("httpStatus", response.getStatusCode().toString());
			result.put("tracksReturned", response.getBody() != null ? response.getBody().size() : 0);
			result.put("clientIdConfigured", clientId != null && !clientId.isEmpty());
			if (response.getBody() != null && !response.getBody().isEmpty()) {
				result.put("sampleTrack", response.getBody().get(0));
			}
			return result;
		} catch (Exception e) {
			Map<String, Object> result = new HashMap<>();
			result.put("status", "error");
			result.put("error", e.getMessage());
			result.put("errorType", e.getClass().getSimpleName());
			e.printStackTrace();
			return result;
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
		status.put("hasValidToken", soundWrappedService.getTokenStore().hasValidToken());
		status.put("needsRefresh", soundWrappedService.getTokenStore().needsRefresh());
		
		// Include expiration info if available
		var tokenOpt = soundWrappedService.getTokenStore().getToken();
		if (tokenOpt.isPresent()) {
			var token = tokenOpt.get();
			status.put("expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : "unknown");
			status.put("isExpired", token.isExpired());
			status.put("isExpiringSoon", token.isExpiredOrExpiringSoon());
		}
		
		return status;
	}

	/**
	 * Proactively refresh the access token.
	 * This endpoint can be called by the browser extension or frontend
	 * to ensure tokens stay fresh even during inactivity.
	 */
	@PostMapping("/refresh-token")
	public Map<String, Object> refreshToken() {
		Map<String, Object> result = new HashMap<>();
		try {
			String refreshToken = soundWrappedService.getTokenStore().getRefreshToken();
			if (refreshToken == null || refreshToken.isBlank()) {
				result.put("success", false);
				result.put("message", "No refresh token available. User must re-authenticate.");
				return result;
			}

			String newAccessToken = soundWrappedService.refreshAccessToken(refreshToken);
			result.put("success", true);
			result.put("message", "Token refreshed successfully");
			result.put("hasAccessToken", newAccessToken != null);
			return result;
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", "Failed to refresh token: " + e.getMessage());
			result.put("error", e.getClass().getSimpleName());
			return result;
		}
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