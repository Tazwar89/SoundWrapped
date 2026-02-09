package com.soundwrapped.service;

import com.soundwrapped.exception.*;
import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

/**
 * Service for interacting with the SoundCloud API.
 * Handles API requests, token refresh, and data aggregation
 * for the "Wrapped" experience.
 * 
 * @author Tazwar Sikder
 */
@Service
public class SoundWrappedService {
	private static final String TOKEN_URL = "https://api.soundcloud.com/oauth2/token";

	@Value("${soundcloud.api.base-url:https://api.soundcloud.com}")
	private String soundCloudApiBaseUrl;

	@Value("${soundcloud.client-id}")
	private String clientId;

	@Value("${soundcloud.client-secret}")
	private String clientSecret;

	@Value("${google.knowledge-graph.api-key}")
	private String googleKnowledgeGraphApiKey;

	@Value("${openai.api-key:}")
	private String openaiApiKey;

	@Value("${groq.api-key:}")
	private String groqApiKey;

	@Value("${groq.base-url:https://api.groq.com/openai/v1}")
	private String groqBaseUrl;

	@Value("${serpapi.api-key:}")
	private String serpApiKey;

	private final TokenStore tokenStore;
	private final RestTemplate restTemplate;
	private final GenreAnalysisService genreAnalysisService;
	private final UserActivityRepository userActivityRepository;
	private final ActivityTrackingService activityTrackingService;
	private final LyricsService lyricsService;
	private final EnhancedArtistService enhancedArtistService;
	
	// Daily cache for "Genre of the Day"
	private static Map<String, Object> cachedGenreOfTheDay = null;
	private static LocalDate cachedGenreDate = null;
	
	// Daily cache for "Song of the Day"
	private static Map<String, Object> cachedSongOfTheDay = null;
	private static LocalDate cachedSongDate = null;
	
	// Daily cache for "Artist of the Day"
	private static Map<String, Object> cachedArtistOfTheDay = null;
	private static LocalDate cachedArtistDate = null;

	public SoundWrappedService(
			TokenStore tokenStore, 
			RestTemplate restTemplate,
			GenreAnalysisService genreAnalysisService,
			UserActivityRepository userActivityRepository,
			ActivityTrackingService activityTrackingService,
			LyricsService lyricsService,
			EnhancedArtistService enhancedArtistService) {
		this.tokenStore = tokenStore;
		this.restTemplate = restTemplate;
		this.genreAnalysisService = genreAnalysisService;
		this.userActivityRepository = userActivityRepository;
		this.activityTrackingService = activityTrackingService;
		this.lyricsService = lyricsService;
		this.enhancedArtistService = enhancedArtistService;
	}
	
	/**
	 * Clear caches on startup to ensure fresh data with tracks
	 */
	@PostConstruct
	public void clearCachesOnStartup() {
		System.out.println("Clearing daily caches on startup to ensure fresh data...");
		cachedGenreOfTheDay = null;
		cachedGenreDate = null;
		cachedSongOfTheDay = null;
		cachedSongDate = null;
		cachedArtistOfTheDay = null;
		cachedArtistDate = null;
	}

	/**
	 * Clear the daily cache for featured content.
	 * This can be called via API endpoint to force regeneration of descriptions.
	 */
	public void clearFeaturedCache() {
		System.out.println("========================================");
		System.out.println("Manually clearing featured content cache...");
		System.out.println("========================================");
		cachedGenreOfTheDay = null;
		cachedGenreDate = null;
		cachedSongOfTheDay = null;
		cachedSongDate = null;
		cachedArtistOfTheDay = null;
		cachedArtistDate = null;
		System.out.println("✓ Featured content cache cleared. Next requests will generate fresh descriptions with SerpAPI integration.");
	}

	public TokenStore getTokenStore() {
		return tokenStore;
	}

	public String getClientId() {
		return clientId;
	}

	// =========================
	// Core HTTP Helpers
	// =========================

	/**
	 * Send authenticated HTTP GET request (using Spring's RestTemplate utility)
	 * to SoundCloud API.
	 * 
	 * @param url         SoundCloud API endpoint URL
	 * @param accessToken Valid OAuth2 access token
	 * @return            JSON response body parsed into a {@code Map}
	 * @throws            HttpClientErrorException if the request fails
	 */
	private Map<String, Object> makeGetRequest(String url, String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
		headers.set("Accept", "application/json");
		HttpEntity<String> request = new HttpEntity<String>(headers);
		
		try {
		ResponseEntity<Map<String, Object>> response = restTemplate
				.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>(){});

		Map<String, Object> responseBody = response.getBody();
		return responseBody != null ? responseBody : Map.of();
		} catch (org.springframework.web.client.ResourceAccessException e) {
			// Handle timeout or connection issues
			System.out.println("Request timeout or connection error for URL: " + url + " - " + e.getMessage());
			throw new ApiRequestException("Request to SoundCloud API timed out or failed to connect: " + e.getMessage(), e);
		} catch (Exception e) {
			// If the response is not a Map (e.g., it's a List), handle it differently
			try {
				ResponseEntity<List<Map<String, Object>>> listResponse = restTemplate
						.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<List<Map<String, Object>>>(){});
				
				List<Map<String, Object>> listBody = listResponse.getBody();
				if (listBody != null) {
					Map<String, Object> result = new HashMap<String, Object>();
					result.put("collection", listBody);
					return result;
				}
			} catch (org.springframework.web.client.ResourceAccessException listException) {
				System.out.println("Request timeout or connection error (List) for URL: " + url + " - " + listException.getMessage());
				throw new ApiRequestException("Request to SoundCloud API timed out or failed to connect: " + listException.getMessage(), listException);
			} catch (Exception listException) {
				System.out.println("Failed to parse response as Map or List: " + e.getMessage());
			}
			
			throw e;
		}
	}

	/**
     * Send authenticated HTTP GET request with automatic token refresh.
     * If the access token has expired, it is refreshed using the refresh token.
     *
     */
	public Map<String, Object> makeGetRequestWithRefresh(String url) {
		String currentAccessToken = tokenStore.getAccessToken();

		if (currentAccessToken == null) {
			throw new ApiRequestException("No access token available. User must authenticate first.");
		}

		try {
			return makeGetRequest(url, tokenStore.getAccessToken());
		}
		catch (org.springframework.web.client.ResourceAccessException rae) {
			// Handle timeouts and connection issues
			throw new ApiRequestException("Request to SoundCloud API timed out or failed to connect: " + rae.getMessage(), rae);
		}
		catch (HttpClientErrorException htee) {
			if (htee.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				try {
					//Access token expired → refresh it
					String newAccessToken = refreshAccessToken(tokenStore.getRefreshToken());

					//Optionally update stored access token in DB here
					return makeGetRequest(url, newAccessToken);
				}

				catch (TokenRefreshException tre) {
					throw new ApiRequestException("Failed to refresh access token during GET request.", tre);
				}
			}

			throw new ApiRequestException("GET request failed: " + htee.getMessage(), htee);
		}
		catch (ApiRequestException are) {
			// Re-throw ApiRequestException as-is
			throw are;
		}
		catch (Exception e) {
			// Catch any other exceptions
			throw new ApiRequestException("Unexpected error during GET request: " + e.getMessage(), e);
		}
	}

	// =========================
	// OAuth Flows
	// =========================

	/**
	 * Refreshes the SoundCloud OAuth2 access token using the provided refresh token.
	 * <p>
	 * Sends a POST request to SoundCloud's token endpoint and returns the new access token.
	 * 
	 * @return New access token as a {@code String}
	 * @throws HttpClientErrorException if the request fails
	 */
	public String refreshAccessToken(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new TokenRefreshException("Missing refresh token; cannot refresh access token.");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");

		MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
		body.add("grant_type", "refresh_token");
		body.add("refresh_token", refreshToken);
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);

		try {
			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(body, headers);
			ResponseEntity<Map<String, Object>> response = restTemplate
					.exchange(TOKEN_URL, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>(){});

			Map<String, Object> responseBody = response.getBody();

			if (responseBody == null || !responseBody.containsKey("access_token")) {
				throw new TokenRefreshException("No access token in refresh response: " + responseBody);
			}

			//Update refresh token if SoundCloud issues a new one
			String newAccessToken = (String) responseBody.get("access_token");
			String newRefreshToken = (String) responseBody.get("refresh_token");

			// Extract expires_in if provided (SoundCloud OAuth2 tokens typically include this)
			Integer expiresInSeconds = null;
			Object expiresInObj = responseBody.get("expires_in");
			if (expiresInObj instanceof Number) {
				expiresInSeconds = ((Number) expiresInObj).intValue();
			}

			//Always persist through TokenStore with expiration info
			tokenStore.saveTokens(newAccessToken, newRefreshToken != null ? newRefreshToken : refreshToken, expiresInSeconds);

			return newAccessToken;
		}

		catch (Exception e) {
			throw new TokenRefreshException("Failed to refresh access token", e);
		}
	}

	public Map<String, Object> exchangeAuthorizationCode(String code) {
		if (code == null || code.isBlank()) {
			throw new TokenExchangeException("Authorization code must not be empty.");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");

		MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);
		body.add("grant_type", "authorization_code");
		body.add("redirect_uri", "http://localhost:8080/callback");
		body.add("code", code);

		try {
			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(body, headers);
			ResponseEntity<Map<String, Object>> response = restTemplate
					.exchange(TOKEN_URL, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>(){});

			Map<String, Object> responseBody = response.getBody();

			if (responseBody == null
					|| !responseBody.containsKey("access_token")
					|| !responseBody.containsKey("refresh_token")) {
				throw new TokenExchangeException("Invalid response from authorization exchange: " + responseBody);
			}

			String accessToken = (String) responseBody.get("access_token");
			String refreshToken = (String) responseBody.get("refresh_token");
			
			// Extract expires_in if provided
			Integer expiresInSeconds = null;
			Object expiresInObj = responseBody.get("expires_in");
			if (expiresInObj instanceof Number) {
				expiresInSeconds = ((Number) expiresInObj).intValue();
			}
			
			tokenStore.saveTokens(accessToken, refreshToken, expiresInSeconds);

			return responseBody;
		}

		catch (Exception e) {
			throw new TokenExchangeException("Failed to exchange authorization code.", e);
		}
	}

	// =========================
	// Pagination Helper
	// =========================

	/**
	 * Fetches paginated results (with auto-refresh on 401).
	 * Includes timeout protection and maximum page limit to prevent hanging.
	 * 
	 * @return JSON response body as a {@code List} of {@code Map}s
	 */
	private List<Map<String, Object>> fetchPaginatedResultsWithRefresh(String url) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();
		int maxPages = 10; // Limit pagination to prevent infinite loops
		int pageCount = 0;

		while (url != null && pageCount < maxPages) {
			try {
			Map<String, Object> response = makeGetRequestWithRefresh(url);
			List<Map<String, Object>> pageResults = new ArrayList<Map<String, Object>>();
				
				// Handle different response structures
			Object rawCollection = response.get("collection");
				
				// If no collection field, check if response is directly a list
				if (rawCollection == null && response instanceof List<?>) {
					rawCollection = response;
				}

			if (rawCollection instanceof List<?>) {
				for (Object item : (List<?>) rawCollection) {
					if (item instanceof Map<?, ?> rawMap) {
						Map<String, Object> safeMap = new HashMap<String, Object>();

						for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
							if (entry.getKey() instanceof String) {
								safeMap.put((String) entry.getKey(), entry.getValue());
							}
						}

						pageResults.add(safeMap);
					}
				}
			}

			if (!pageResults.isEmpty()) {
				paginatedResults.addAll(pageResults);
			}

			url = (String) response.get("next_href");
				pageCount++;
			} catch (Exception e) {
				// If pagination fails, return what we have so far
				System.out.println("Error fetching paginated results at page " + pageCount + ": " + e.getMessage());
				break;
			}
		}

		return paginatedResults;
	}

	// =========================
	// Utilities for Wrapped
	// =========================

	private ZonedDateTime parsedDate(String createdAt) {
		//SoundCloud createdAt format example: "2013/03/23 14:58:27 +0000"
		SimpleDateFormat original = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
		//Formatted convention for locale-based ZonedDateTime
		SimpleDateFormat formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		Date parsedDate = null;

		try {
			parsedDate = original.parse(createdAt);
		}

		catch (ParseException pe) {
			System.out.println("Invalid date!");
		}

		String formattedDate = formatted.format(parsedDate);

		return ZonedDateTime.parse(formattedDate);
	}

	private int calculateAccountAgeYears(String createdAt) {
		return ZonedDateTime.now().getYear() - parsedDate(createdAt).getYear();
	}

	private Map<Integer, Integer> countYears(List<Map<String, Object>> tracks) throws ParseException {
		Map<Integer, Integer> yearCounts = new HashMap<Integer, Integer>();

		for (Map<String, Object> track : tracks) {
			if (track.get("created_at") instanceof String createdAtStr) {
				int year = parsedDate(createdAtStr).getYear();
				yearCounts.put(year, yearCounts.getOrDefault(year, 0) + 1);
			}
		}

		return yearCounts;
	}

	private Map<String, Integer> countTopArtists(List<Map<String, Object>> tracks) {
		Map<String, Integer> artistCounts = new HashMap<String, Integer>();

		for (Map<String, Object> track : tracks) {
			Object userObj = track.get("user");

			if (userObj != null && userObj instanceof Map<?, ?> rawUser) {
				Object usernameObj = rawUser.get("username");

				if (usernameObj != null && usernameObj instanceof String artist) {
					artistCounts.put(artist, artistCounts.getOrDefault(artist, 0) + 1);
				}
			}
		}

		return artistCounts;
	}

	private Map<String, Long> calculateArtistListeningMs(List<Map<String, Object>> tracks) {
		Map<String, Long> artistListeningMs = new HashMap<String, Long>();

		for (Map<String, Object> track : tracks) {
			Object userObj = track.get("user");

			if (userObj != null && userObj instanceof Map<?, ?> rawUser) {
				Object usernameObj = rawUser.get("username");

				if (usernameObj != null && usernameObj instanceof String artist) {
					long duration = ((Number) track.getOrDefault("duration", 0)).longValue();
					artistListeningMs.put(artist, artistListeningMs.getOrDefault(artist, 0L) + duration);
				}
			}
		}

		return artistListeningMs;
	}

	/**
	 * 
	 * @param map any instance of Number + null
	 * @param n
	 * @return
	 */
	private List<String> topNKeys(Map<String, ? extends Number> map, int n) {
		return map.entrySet().stream()
			.sorted((a, b) -> Double.compare(
					b.getValue().doubleValue(),
					a.getValue().doubleValue()))
			.limit(n).map(Map.Entry::getKey).toList();
	}

	private List<Map<String, Object>> topRepostedTracks(
			List<Map<String, Object>> tracks, int n) {
		return tracks.stream().sorted((a, b) -> Integer.compare(
				((Number) b.getOrDefault("reposts_safe", 0)).intValue(),
				((Number) a.getOrDefault("reposts_safe", 0)).intValue()))
				.limit(n).map(track -> Map.of(
						"title", track.getOrDefault("title", "Unknown"),
						"reposts",track.getOrDefault("reposts_safe", 0)))
				.toList();
    }

	private Map<String, Double> convertMsToHours(Map<String, Long> artistListeningMs) {
		Map<String, Double> hoursMap = new HashMap<String, Double>();

		for (Map.Entry<String, Long> entry : artistListeningMs.entrySet()) {
			hoursMap.put(entry.getKey(), entry.getValue() / 1000.0 / 60.0 / 60.0);
		}

		return hoursMap;
	}

	/**
	 * 
	 * @param prefix
	 * @param limit
	 * @return
	 */
	private String urlExtension(String prefix, int limit) {
		return String.format("%s?linked_partitioning=true&limit=%d", prefix, limit);
	}

	// ==============================
	// Public API Used by Controllers
	// ==============================

	/**
     * Retrieves the authenticated user's SoundCloud profile.
     *
     * @return             Profile information as a {@code Map}
     */
	public Map<String, Object> getUserProfile() {
		try {
		String url = soundCloudApiBaseUrl + "/me";
		return makeGetRequestWithRefresh(url);
		} catch (ApiRequestException e) {
			// Re-throw ApiRequestException as-is (expected by tests and callers)
			throw e;
		} catch (Exception e) {
			System.out.println("Error fetching user profile: " + e.getMessage());
			// Return empty profile on unexpected errors only
			Map<String, Object> errorProfile = new HashMap<String, Object>();
			errorProfile.put("error", "Unable to fetch profile data");
			errorProfile.put("message", e.getMessage());
			errorProfile.put("username", "Unknown");
			errorProfile.put("followers_count", 0);
			errorProfile.put("followings_count", 0);
			return errorProfile;
		}
	}

	/**
     * Retrieves the user's liked tracks.
     * 
     * @return {@code List} of liked tracks
     */
	public List<Map<String, Object>> getUserLikes() {
		try {
		String url = soundCloudApiBaseUrl + "/me/favorites";
		return fetchPaginatedResultsWithRefresh(url);
		} catch (Exception e) {
			System.out.println("Error fetching likes: " + e.getMessage());
			return new ArrayList<Map<String, Object>>();
		}
    }

	/**
     * Retrieves the user's playlists.
     * 
     * @return {@code List} of playlists
     */
	public List<Map<String, Object>> getUserPlaylists() {
		try {
		String url = soundCloudApiBaseUrl + urlExtension("/me/playlists", 50);
		return fetchPaginatedResultsWithRefresh(url);
		} catch (Exception e) {
			System.out.println("Error fetching playlists: " + e.getMessage());
			return new ArrayList<Map<String, Object>>();
		}
    }

	/**
     * Retrieves the user's followers.
     * 
     * @return {@code List} of followers
     */
	public List<Map<String, Object>> getUserFollowers() {
		try {
		String url = soundCloudApiBaseUrl + urlExtension("/me/followers", 50);
			return fetchPaginatedResultsWithRefresh(url);
		} catch (Exception e) {
			System.out.println("Error fetching followers: " + e.getMessage());
			return new ArrayList<Map<String, Object>>();
		}
    }

	/**
     * Retrieves the users that the authenticated user is following.
     * 
     * @return {@code List} of users being followed
     */
	public List<Map<String, Object>> getUserFollowings() {
		try {
			String url = soundCloudApiBaseUrl + urlExtension("/me/followings", 50);
		return fetchPaginatedResultsWithRefresh(url);
		} catch (Exception e) {
			System.out.println("Error fetching followings: " + e.getMessage());
			return new ArrayList<Map<String, Object>>();
		}
    }

	/**
	 * Get recent activity from SoundCloud (likes, uploads, follows).
	 * Combines and sorts by timestamp (most recent first).
	 * Uses tracked activity timestamps from database for likes/reposts when available.
	 * 
	 * @param limit Maximum number of activities to return
	 * @return List of activity items with type, track/user info, and timestamp
	 */
	public List<Map<String, Object>> getRecentActivity(int limit) {
		List<Map<String, Object>> activities = new ArrayList<Map<String, Object>>();
		
		// Get user ID for querying tracked activities
		String userId = "";
		try {
			Map<String, Object> profile = getUserProfile();
			userId = String.valueOf(profile.getOrDefault("id", ""));
		} catch (Exception e) {
			System.err.println("Error getting user profile for recent activity: " + e.getMessage());
		}
		
		// Build a map of trackId -> most recent LIKE activity timestamp from database
		Map<String, java.time.LocalDateTime> trackedLikeTimestamps = new HashMap<String, java.time.LocalDateTime>();
		if (!userId.isEmpty()) {
			try {
				List<com.soundwrapped.entity.UserActivity> likeActivities = 
					userActivityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
						userId, 
						com.soundwrapped.entity.UserActivity.ActivityType.LIKE,
						java.time.LocalDateTime.now().minusYears(1), // Last year
						java.time.LocalDateTime.now()
					);
				// Get most recent timestamp for each track
				for (com.soundwrapped.entity.UserActivity activity : likeActivities) {
					String trackId = activity.getTrackId();
					java.time.LocalDateTime activityTime = activity.getCreatedAt();
					trackedLikeTimestamps.putIfAbsent(trackId, activityTime);
					if (activityTime.isAfter(trackedLikeTimestamps.get(trackId))) {
						trackedLikeTimestamps.put(trackId, activityTime);
					}
				}
			} catch (Exception e) {
				System.err.println("Error fetching tracked like activities: " + e.getMessage());
			}
		}
		
		// Build a map of trackId -> most recent REPOST activity timestamp from database
		Map<String, java.time.LocalDateTime> trackedRepostTimestamps = new HashMap<String, java.time.LocalDateTime>();
		if (!userId.isEmpty()) {
			try {
				List<com.soundwrapped.entity.UserActivity> repostActivities = 
					userActivityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
						userId, 
						com.soundwrapped.entity.UserActivity.ActivityType.REPOST,
						java.time.LocalDateTime.now().minusYears(1), // Last year
						java.time.LocalDateTime.now()
					);
				// Get most recent timestamp for each track
				for (com.soundwrapped.entity.UserActivity activity : repostActivities) {
					String trackId = activity.getTrackId();
					java.time.LocalDateTime activityTime = activity.getCreatedAt();
					trackedRepostTimestamps.putIfAbsent(trackId, activityTime);
					if (activityTime.isAfter(trackedRepostTimestamps.get(trackId))) {
						trackedRepostTimestamps.put(trackId, activityTime);
					}
				}
			} catch (Exception e) {
				System.err.println("Error fetching tracked repost activities: " + e.getMessage());
			}
		}
		
		try {
			// Get recent favorites (likes)
			List<Map<String, Object>> favorites = getUserLikes();
			if (favorites != null) {
				for (Map<String, Object> favorite : favorites) {
					if (favorite != null) {
						try {
							Map<String, Object> activity = new HashMap<String, Object>();
							activity.put("type", "like");
							activity.put("track", favorite);
							
							// Use tracked activity timestamp if available, otherwise fall back to track created_at
							String trackId = String.valueOf(favorite.getOrDefault("id", ""));
							java.time.LocalDateTime trackedTimestamp = trackedLikeTimestamps.get(trackId);
							
							if (trackedTimestamp != null) {
								// Convert LocalDateTime to ISO 8601 string format (compatible with JavaScript Date)
								// Format: YYYY-MM-DDTHH:mm:ssZ (assume UTC timezone)
								String isoTimestamp = trackedTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
								activity.put("timestamp", isoTimestamp);
							} else {
								// Fall back to track creation date (not ideal, but better than nothing)
								Object createdAt = favorite.get("created_at");
								activity.put("timestamp", createdAt != null ? createdAt.toString() : "");
							}
							
							activities.add(activity);
						} catch (Exception e) {
							System.out.println("Error processing favorite: " + e.getMessage());
							// Continue with next favorite
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error fetching favorites for recent activity: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			// Get recent uploads
			String url = soundCloudApiBaseUrl + urlExtension("/me/tracks", 50);
			List<Map<String, Object>> uploads = fetchPaginatedResultsWithRefresh(url);
			if (uploads != null) {
				for (Map<String, Object> upload : uploads) {
					if (upload != null) {
						try {
							Map<String, Object> activity = new HashMap<String, Object>();
							activity.put("type", "upload");
							activity.put("track", upload);
							Object createdAt = upload.get("created_at");
							activity.put("timestamp", createdAt != null ? createdAt.toString() : "");
							activities.add(activity);
						} catch (Exception e) {
							System.out.println("Error processing upload: " + e.getMessage());
							// Continue with next upload
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error fetching uploads for recent activity: " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			// Get recent followings
			List<Map<String, Object>> followings = getUserFollowings();
			if (followings != null) {
				for (Map<String, Object> following : followings) {
					if (following != null) {
						try {
							Map<String, Object> activity = new HashMap<String, Object>();
							activity.put("type", "follow");
							activity.put("user", following);
							Object createdAt = following.get("created_at");
							activity.put("timestamp", createdAt != null ? createdAt.toString() : "");
							activities.add(activity);
						} catch (Exception e) {
							System.out.println("Error processing following: " + e.getMessage());
							// Continue with next following
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error fetching followings for recent activity: " + e.getMessage());
			e.printStackTrace();
		}
		
		// Sort by timestamp (most recent first)
		try {
			activities.sort((a, b) -> {
				try {
					Object timestampAObj = a.getOrDefault("timestamp", "");
					Object timestampBObj = b.getOrDefault("timestamp", "");
					
					// Handle null or non-string timestamps
					String timestampA = timestampAObj != null ? timestampAObj.toString() : "";
					String timestampB = timestampBObj != null ? timestampBObj.toString() : "";
					
					// If both are empty, maintain order
					if (timestampA.isEmpty() && timestampB.isEmpty()) {
						return 0;
					}
					// Empty timestamps go to the end
					if (timestampA.isEmpty()) {
						return 1;
					}
					if (timestampB.isEmpty()) {
						return -1;
					}
					
					// Compare timestamps (ISO 8601 format) - most recent first
					return timestampB.compareTo(timestampA);
				} catch (Exception e) {
					System.err.println("Error comparing timestamps: " + e.getMessage());
					return 0; // Maintain order on error
				}
			});
		} catch (Exception e) {
			System.err.println("Error sorting activities: " + e.getMessage());
			e.printStackTrace();
			// Continue without sorting
		}
		
		// Limit results
		try {
			return activities.stream().limit(limit).collect(Collectors.toList());
		} catch (Exception e) {
			System.err.println("Error limiting activities: " + e.getMessage());
			e.printStackTrace();
			// Return all activities if limit fails
			return activities.size() > limit ? activities.subList(0, limit) : activities;
		}
	}

	/**
     * Retrieves the user's tracks (uploaded tracks, or liked tracks if no uploads).
     * 
     * @return {@code List} of tracks
     */
	public List<Map<String, Object>> getUserTracks() {
		// First try to get uploaded tracks
		try {
		String url = soundCloudApiBaseUrl + urlExtension("/me/tracks", 50);
			List<Map<String, Object>> uploadedTracks = fetchPaginatedResultsWithRefresh(url);
			
			// If user has uploaded tracks, return them
			if (!uploadedTracks.isEmpty()) {
				return uploadedTracks;
			}
		} catch (Exception e) {
			// If uploaded tracks fail (user has no uploads), fall back to liked tracks
			System.out.println("No uploaded tracks found, falling back to liked tracks: " + e.getMessage());
		}
		
		// Fall back to liked tracks if no uploaded tracks
		try {
			String url = soundCloudApiBaseUrl + urlExtension("/me/favorites", 50);
		return fetchPaginatedResultsWithRefresh(url);
		} catch (Exception e) {
			System.out.println("Failed to fetch liked tracks: " + e.getMessage());
			return new ArrayList<Map<String, Object>>();
		}
	}

	/**
	 * Gets user's top tracks based on their tracked play activity in the database.
	 * Play activity is tracked by the browser extension when users play tracks on SoundCloud.com.
	 * Combines database play counts (from extension tracking) with SoundCloud track details.
	 * 
	 * @param userId SoundCloud user ID
	 * @param limit Maximum number of tracks to return
	 * @return List of tracks sorted by user's play count (most played first)
	 */
	public List<Map<String, Object>> getUserTopTracksByPlays(String userId, int limit) {
		try {
			// Get date range (all time for now)
			java.time.LocalDateTime startDate = java.time.LocalDateTime.of(2000, 1, 1, 0, 0);
			java.time.LocalDateTime endDate = java.time.LocalDateTime.now();
			
			// Get most played tracks from database
			List<Object[]> mostPlayed = userActivityRepository.getMostPlayedTracks(userId, startDate, endDate);
			
			if (mostPlayed.isEmpty()) {
				// If no tracked plays, fall back to regular getUserTracks sorted by global playback_count
				List<Map<String, Object>> allTracks = getUserTracks();
				return allTracks.stream()
					.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("playback_count", 0)).longValue(),
						((Number) a.getOrDefault("playback_count", 0)).longValue()))
					.limit(limit)
					.collect(java.util.stream.Collectors.toList());
			}
			
			// Create a map of trackId -> userPlayCount
			Map<String, Long> trackPlayCounts = new HashMap<String, Long>();
			for (Object[] result : mostPlayed) {
				String trackId = String.valueOf(result[0]);
				Long playCount = ((Number) result[1]).longValue();
				trackPlayCounts.put(trackId, playCount);
			}
			
			// Fetch track details from SoundCloud API for each tracked track
			List<Map<String, Object>> topTracks = new ArrayList<Map<String, Object>>();
			for (String trackId : trackPlayCounts.keySet()) {
				try {
					String url = soundCloudApiBaseUrl + "/tracks/" + trackId;
					Map<String, Object> track = makeGetRequestWithRefresh(url);
					
					// Add user's play count to track data
					track.put("user_play_count", trackPlayCounts.get(trackId));
					topTracks.add(track);
				} catch (Exception e) {
					// Skip tracks that can't be fetched (may have been deleted)
					System.out.println("Could not fetch track " + trackId + ": " + e.getMessage());
				}
			}
			
			// Sort by user's play count (descending) and limit
			return topTracks.stream()
				.sorted((a, b) -> Long.compare(
					((Number) b.getOrDefault("user_play_count", 0)).longValue(),
					((Number) a.getOrDefault("user_play_count", 0)).longValue()))
				.limit(limit)
				.collect(java.util.stream.Collectors.toList());
				
		} catch (Exception e) {
			System.out.println("Error fetching user top tracks by plays: " + e.getMessage());
			// Fall back to regular getUserTracks
			return getUserTracks().stream()
				.sorted((a, b) -> Long.compare(
					((Number) b.getOrDefault("playback_count", 0)).longValue(),
					((Number) a.getOrDefault("playback_count", 0)).longValue()))
				.limit(limit)
				.collect(java.util.stream.Collectors.toList());
		}
	}

	/**
     * Retrieves the user's liked tracks.
     * 
     * @param trackUrn
     * @return {@code List} of liked tracks
     */
	public List<Map<String, Object>> getRelatedTracks(String trackUrn) {
		String url = soundCloudApiBaseUrl + urlExtension("/tracks/" + trackUrn + "/related", 10);

		return fetchPaginatedResultsWithRefresh(url);
	}

	/**
	 * Gets an access token for API requests. Uses user's token if available, otherwise client credentials.
	 * 
	 * @return Access token string, or null if unavailable
	 */
	private String getAccessTokenForRequest() {
		// Try to use user's access token first
		var tokenOpt = tokenStore.getToken();
		if (tokenOpt.isPresent()) {
			Token token = tokenOpt.get();
			if (!token.isExpired()) {
				return token.getAccessToken();
			} else {
				// Token is expired, try to refresh it
				try {
					String refreshToken = token.getRefreshToken();
					if (refreshToken != null && !refreshToken.isBlank()) {
						System.out.println("Token expired, attempting to refresh...");
						String newAccessToken = refreshAccessToken(refreshToken);
						if (newAccessToken != null) {
							return newAccessToken;
						}
					}
				} catch (Exception e) {
					System.err.println("Failed to refresh user token: " + e.getMessage());
					// Fall through to client credentials
				}
			}
		}
		
		// If user token not available or refresh failed, use client credentials token
		System.out.println("Using client credentials token for public endpoints");
		return getClientCredentialsToken();
	}

	/**
	 * Gets an app-level access token using client credentials flow.
	 * This allows public endpoints to work without user authentication.
	 * 
	 * @return Access token string, or null if unavailable
	 */
	private String getClientCredentialsToken() {
		try {
			System.out.println("Attempting to get client credentials token...");
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");

			MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
			body.add("grant_type", "client_credentials");
			body.add("client_id", clientId);
			body.add("client_secret", clientSecret);

			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(body, headers);
			ResponseEntity<Map<String, Object>> response = restTemplate
					.exchange(TOKEN_URL, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>(){});

			Map<String, Object> responseBody = response.getBody();
			if (responseBody == null || !responseBody.containsKey("access_token")) {
				System.err.println("Failed to get client credentials token. Response: " + responseBody);
				System.err.println("Note: SoundCloud may not support client_credentials grant type. Falling back to null.");
				return null;
			}

			String accessToken = (String) responseBody.get("access_token");
			System.out.println("Successfully obtained client credentials token (length: " + (accessToken != null ? accessToken.length() : 0) + ")");
			return accessToken;
		} catch (org.springframework.web.client.HttpClientErrorException e) {
			System.err.println("HTTP error getting client credentials token: " + e.getStatusCode() + " - " + e.getMessage());
			System.err.println("Response body: " + e.getResponseBodyAsString());
			System.err.println("Note: SoundCloud may not support client_credentials grant type.");
			return null;
		} catch (Exception e) {
			System.err.println("Error getting client credentials token: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Fetches tracks from a SoundCloud playlist by its permalink URL.
	 * Uses the /resolve endpoint to get playlist info, then fetches tracks.
	 * 
	 * @param playlistUrl Full SoundCloud playlist URL (e.g., "https://soundcloud.com/music-charts-us/sets/all-music-genres")
	 * @return List of tracks from the playlist, or empty list if fetch fails
	 */
	@SuppressWarnings("unused")
	private List<Map<String, Object>> getTracksFromPlaylist(String playlistUrl) {
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for playlist request. User may need to authenticate.");
				return new ArrayList<Map<String, Object>>();
			}
			
			// Try alternative approach: extract username and set name from URL
			// URL format: https://soundcloud.com/{username}/sets/{set-name}
			// For example: https://soundcloud.com/music-charts-us/sets/all-music-genres
			String username = null;
			String setName = null;
			try {
				java.net.URI uri = java.net.URI.create(playlistUrl);
				String path = uri.getPath();
				// Remove leading slash and split
				String[] parts = path.startsWith("/") ? path.substring(1).split("/") : path.split("/");
				if (parts.length >= 3 && parts[1].equals("sets")) {
					username = parts[0];
					setName = parts[2];
					System.out.println("Extracted username: " + username + ", set name: " + setName);
				}
			} catch (Exception e) {
				System.err.println("Error parsing playlist URL: " + e.getMessage());
			}
			
			// First, try to get user's playlists to find the set
			if (username != null && setName != null) {
				try {
					String userPlaylistsUrl = soundCloudApiBaseUrl + "/users/" + username + "/playlists?limit=200&linked_partitioning=true";
					System.out.println("Attempting to fetch playlists from user: " + userPlaylistsUrl);
					
					HttpHeaders headers = new HttpHeaders();
					headers.setBearerAuth(accessToken);
					headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
					headers.set("Accept", "application/json");
					HttpEntity<String> request = new HttpEntity<String>(headers);
					
					ResponseEntity<Map<String, Object>> playlistsResponse = restTemplate.exchange(
						userPlaylistsUrl,
						HttpMethod.GET,
						request,
						new ParameterizedTypeReference<Map<String, Object>>(){}
					);
					
					if (playlistsResponse.getStatusCode().is2xxSuccessful() && playlistsResponse.getBody() != null) {
						Map<String, Object> playlistsBody = playlistsResponse.getBody();
						if (playlistsBody == null) {
							System.err.println("Playlists response body is null");
							return new ArrayList<Map<String, Object>>();
						}
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> playlists = (List<Map<String, Object>>) playlistsBody.get("collection");
						
						if (playlists == null && playlistsBody instanceof List) {
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> directPlaylists = (List<Map<String, Object>>) playlistsBody;
							playlists = directPlaylists;
						}
						
						if (playlists != null) {
							// Find the playlist with matching permalink or title
							for (Map<String, Object> playlist : playlists) {
								String playlistPermalink = (String) playlist.getOrDefault("permalink", "");
								String playlistTitle = (String) playlist.getOrDefault("title", "");
								
								if (playlistPermalink.equals(setName) || playlistTitle.toLowerCase().contains(setName.toLowerCase())) {
									System.out.println("Found matching playlist: " + playlistTitle + " (ID: " + playlist.get("id") + ")");
									
									// Get tracks from this playlist
									Object playlistIdObj = playlist.get("id");
									if (playlistIdObj != null) {
										String playlistId = String.valueOf(playlistIdObj);
										return getTracksFromPlaylistById(playlistId, accessToken);
									}
								}
							}
							System.err.println("Playlist '" + setName + "' not found in user '" + username + "' playlists");
						}
					}
				} catch (Exception e) {
					System.err.println("Error fetching user playlists: " + e.getMessage());
					e.printStackTrace();
				}
			}
			
			// Fallback: Use /resolve endpoint to get playlist info
			String resolveUrl = soundCloudApiBaseUrl + "/resolve?url=" + java.net.URLEncoder.encode(playlistUrl, "UTF-8");
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			// Resolve playlist
			System.out.println("Attempting to resolve playlist: " + playlistUrl);
			System.out.println("Resolve URL: " + resolveUrl);
			System.out.println("Using access token (length: " + (accessToken != null ? accessToken.length() : 0) + ")");
			
			ResponseEntity<Map<String, Object>> resolveResponse;
			try {
				resolveResponse = restTemplate.exchange(
					resolveUrl,
					HttpMethod.GET,
					request,
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
			} catch (org.springframework.web.client.HttpClientErrorException e) {
				System.err.println("HTTP error resolving playlist " + playlistUrl + ": " + e.getStatusCode() + " - " + e.getMessage());
				System.err.println("Response body: " + e.getResponseBodyAsString());
				return new ArrayList<Map<String, Object>>();
			} catch (Exception e) {
				System.err.println("Exception resolving playlist " + playlistUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Resolve response status: " + resolveResponse.getStatusCode());
			Map<String, Object> playlist = resolveResponse.getBody();
			if (playlist == null) {
				System.err.println("Playlist resolve returned null for: " + playlistUrl);
				System.err.println("Response status: " + resolveResponse.getStatusCode());
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Resolved playlist, keys: " + playlist.keySet());
			System.out.println("Resolved playlist kind: " + playlist.get("kind"));
			
			// Check if it's a playlist or a different type (chart playlists might be "playlist" type)
			String kind = (String) playlist.get("kind");
			if (kind == null || (!kind.equals("playlist") && !kind.equals("set"))) {
				System.err.println("Resolved object is not a playlist. Kind: " + kind + " for URL: " + playlistUrl);
				// Try to get tracks directly if it's a different structure
				Object tracksObj = playlist.get("tracks");
				if (tracksObj instanceof List) {
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> directTracks = (List<Map<String, Object>>) tracksObj;
					System.out.println("Found " + directTracks.size() + " tracks directly in resolved object");
					return directTracks;
				}
				return new ArrayList<Map<String, Object>>();
			}
			
			// Get playlist ID
			Object playlistIdObj = playlist.get("id");
			if (playlistIdObj == null) {
				System.err.println("Playlist ID not found in response for: " + playlistUrl);
				System.err.println("Playlist response keys: " + playlist.keySet());
				return new ArrayList<Map<String, Object>>();
			}
			
			String playlistId = String.valueOf(playlistIdObj);
			System.out.println("Resolved playlist ID: " + playlistId);
			
			return getTracksFromPlaylistById(playlistId, accessToken);
		} catch (Exception e) {
			System.err.println("Error fetching tracks from playlist " + playlistUrl + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}
	
	/**
	 * Helper method to fetch tracks from a playlist by ID.
	 * @param playlistId The playlist ID
	 * @param accessToken The access token for authentication
	 * @return List of tracks from the playlist
	 */
	private List<Map<String, Object>> getTracksFromPlaylistById(String playlistId, String accessToken) {
		try {
			List<Map<String, Object>> allTracks = new ArrayList<Map<String, Object>>();
			String nextUrl = soundCloudApiBaseUrl + "/playlists/" + playlistId + "/tracks?limit=50&linked_partitioning=true";
			
			System.out.println("Fetching tracks from playlist ID: " + playlistId);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			// Fetch all tracks with pagination to ensure we get them in the correct order
			int maxPages = 3; // Limit to prevent infinite loops, but get enough tracks
			int pageCount = 0;
			
			while (nextUrl != null && pageCount < maxPages) {
				System.out.println("Fetching page " + (pageCount + 1) + " from: " + nextUrl);
				
				ResponseEntity<Map<String, Object>> tracksResponse = restTemplate.exchange(
					nextUrl,
					HttpMethod.GET,
					request,
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
				
				System.out.println("Tracks API response status: " + tracksResponse.getStatusCode());
				
				if (tracksResponse.getStatusCode().is2xxSuccessful() && tracksResponse.getBody() != null) {
					Map<String, Object> responseBody = tracksResponse.getBody();
					if (responseBody == null) {
						System.err.println("Tracks response body is null for playlist " + playlistId);
						break;
					}
					System.out.println("Tracks response body keys: " + responseBody.keySet());
					
					// Check if tracks are in a "collection" field (paginated response)
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
					
					if (tracks == null || tracks.isEmpty()) {
						// Try direct list format
						if (responseBody instanceof List) {
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> directTracks = (List<Map<String, Object>>) responseBody;
							tracks = directTracks;
						}
					}
					
					if (tracks != null && !tracks.isEmpty()) {
						allTracks.addAll(tracks);
						System.out.println("Fetched " + tracks.size() + " tracks from page " + (pageCount + 1) + ". Total so far: " + allTracks.size());
						
						// Check for next page URL
						Object nextHref = responseBody.get("next_href");
						if (nextHref != null && nextHref instanceof String) {
							nextUrl = (String) nextHref;
						} else {
							nextUrl = null;
						}
					} else {
						System.err.println("No tracks found in response for playlist " + playlistId + " on page " + (pageCount + 1));
						break;
					}
				} else {
					System.err.println("Failed to fetch tracks from playlist " + playlistId);
					System.err.println("Response status: " + tracksResponse.getStatusCode());
					break;
				}
				
				pageCount++;
			}
			
			if (!allTracks.isEmpty()) {
				System.out.println("Successfully fetched " + allTracks.size() + " total tracks from playlist ID: " + playlistId);
				// Return tracks in the order they were received (playlist order)
				return allTracks;
			} else {
				System.err.println("No tracks found in response for playlist " + playlistId);
			}
			
			return new ArrayList<Map<String, Object>>();
		} catch (Exception e) {
			System.err.println("Error fetching tracks from playlist ID " + playlistId + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}

	/**
	 * Gets popular/trending tracks from SoundCloud's official chart playlists.
	 * Fetches tracks from multiple chart playlists and aggregates them.
	 * Prioritizes "all-music-genres" and "new-hot" playlists.
	 * 
	 * @param limit Maximum number of tracks to return
	 * @return List of popular/trending tracks from chart playlists
	 */
	@org.springframework.cache.annotation.Cacheable(value = "popularTracks", key = "#limit", unless = "#result == null || #result.isEmpty()")
	public List<Map<String, Object>> getPopularTracks(int limit) {
		try {
			// Use the playlist URN directly from the embed code: soundcloud:playlists:1714689261
			// This is the US Top 50 charts playlist: https://soundcloud.com/music-charts-us/sets/all-music-genres
			String playlistUrn = "soundcloud:playlists:1714689261";
			// Extract the playlist ID from the URN (the number after the last colon)
			String playlistId = playlistUrn.substring(playlistUrn.lastIndexOf(':') + 1);
			
			System.out.println("Fetching popular tracks from US chart playlist");
			System.out.println("Playlist URN: " + playlistUrn);
			System.out.println("Extracted playlist ID: " + playlistId);
			
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for popular tracks. User may need to authenticate.");
				return getPopularTracksFallback(limit);
			}
			
			try {
				// Try using the playlist ID directly first (simpler approach)
				System.out.println("Attempting to fetch tracks directly using playlist ID: " + playlistId);
				List<Map<String, Object>> playlistTracks = getTracksFromPlaylistById(playlistId, accessToken);
				
				if (playlistTracks != null && !playlistTracks.isEmpty()) {
					// Log the first few tracks to debug
					System.out.println("First " + Math.min(10, playlistTracks.size()) + " tracks from playlist (before limiting):");
					for (int i = 0; i < Math.min(10, playlistTracks.size()); i++) {
						Map<String, Object> track = playlistTracks.get(i);
						Object userObj = track.get("user");
						String artistName = "Unknown";
						if (userObj instanceof Map) {
							@SuppressWarnings("unchecked")
							Map<String, Object> user = (Map<String, Object>) userObj;
							artistName = (String) user.getOrDefault("username", "Unknown");
						}
						System.out.println("  Track " + (i + 1) + ": " + track.get("title") + " by " + artistName);
					}
					
					// Return the first 'limit' tracks in their original order (first tracks of the playlist)
					// Don't sort - the playlist order is already the chart order
					List<Map<String, Object>> topTracks = playlistTracks.stream()
						.limit(limit)
						.collect(java.util.stream.Collectors.toList());
					
					System.out.println("Fetched " + topTracks.size() + " first tracks from US chart playlist using ID (in original order)");
					return topTracks;
				}
				
				// If direct ID approach fails, try using the URN
				System.out.println("Direct ID approach returned no tracks, trying URN approach...");
				// URL-encode the URN for the API call (colon becomes %3A)
				// Use proper URL encoding for path segments (not form encoding)
				String encodedUrn = playlistUrn.replace(":", "%3A");
				String playlistUrl = soundCloudApiBaseUrl + "/playlists/" + encodedUrn;
				System.out.println("Fetching playlist from: " + playlistUrl);
				System.out.println("Original URN: " + playlistUrn);
				System.out.println("Encoded URN: " + encodedUrn);
				
				HttpHeaders headers = new HttpHeaders();
				headers.setBearerAuth(accessToken);
				headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
				headers.set("Accept", "application/json");
				HttpEntity<String> request = new HttpEntity<String>(headers);
				
				// First, get the playlist to verify it exists and get its tracks
				ResponseEntity<Map<String, Object>> playlistResponse;
				try {
					playlistResponse = restTemplate.exchange(
						playlistUrl,
						HttpMethod.GET,
						request,
						new ParameterizedTypeReference<Map<String, Object>>(){}
					);
				} catch (org.springframework.web.client.HttpClientErrorException e) {
					System.err.println("HTTP error fetching playlist: " + e.getStatusCode() + " - " + e.getMessage());
					System.err.println("Response body: " + e.getResponseBodyAsString());
					throw e;
				}
				
				if (playlistResponse.getStatusCode().is2xxSuccessful() && playlistResponse.getBody() != null) {
					Map<String, Object> playlist = playlistResponse.getBody();
					if (playlist == null) {
						System.err.println("Playlist response body is null");
						return getPopularTracksFallback(limit);
					}
					System.out.println("Successfully fetched playlist. Keys: " + playlist.keySet());
					
					// Get tracks from the playlist - they might be in a "tracks" field or we need to fetch them separately
					Object tracksObj = playlist.get("tracks");
					List<Map<String, Object>> playlistTracksFromResponse = new ArrayList<Map<String, Object>>();
					
					if (tracksObj instanceof List) {
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> directTracks = (List<Map<String, Object>>) tracksObj;
						playlistTracksFromResponse = directTracks;
						System.out.println("Found " + playlistTracksFromResponse.size() + " tracks directly in playlist response");
					} else {
						// Tracks might not be included, try fetching them separately
						Object playlistIdFromResponseObj = playlist.get("id");
						if (playlistIdFromResponseObj != null) {
							String playlistIdFromResponse = String.valueOf(playlistIdFromResponseObj);
							System.out.println("Fetching tracks separately for playlist ID: " + playlistIdFromResponse);
							playlistTracksFromResponse = getTracksFromPlaylistById(playlistIdFromResponse, accessToken);
						}
					}
					
					if (playlistTracksFromResponse != null && !playlistTracksFromResponse.isEmpty()) {
						// Return the first 'limit' tracks in their original order (first tracks of the playlist)
						// Don't sort - the playlist order is already the chart order
						List<Map<String, Object>> topTracks = playlistTracksFromResponse.stream()
							.limit(limit)
							.collect(java.util.stream.Collectors.toList());
						
						System.out.println("Fetched " + topTracks.size() + " first tracks from US chart playlist (in original order)");
						return topTracks;
					} else {
						System.err.println("No tracks found in playlist response");
					}
				} else {
					System.err.println("Failed to fetch playlist. Status: " + playlistResponse.getStatusCode());
				}
			} catch (org.springframework.web.client.HttpClientErrorException e) {
				System.err.println("HTTP error fetching playlist URN " + playlistUrn + ": " + e.getStatusCode() + " - " + e.getMessage());
				System.err.println("Response body: " + e.getResponseBodyAsString());
				System.err.println("Attempted URL: " + soundCloudApiBaseUrl + "/playlists/" + playlistUrn.replace(":", "%3A"));
				e.printStackTrace();
			} catch (Exception e) {
				System.err.println("Failed to fetch from playlist URN " + playlistUrn + ": " + e.getMessage());
				System.err.println("Exception type: " + e.getClass().getSimpleName());
				e.printStackTrace();
			}
			
			// Fallback to alternative method if playlist fetch fails
			System.err.println("Falling back to alternative method for popular tracks");
			return getPopularTracksFallback(limit);
		} catch (Exception e) {
			System.err.println("Error fetching popular tracks from charts: " + e.getMessage());
			e.printStackTrace();
			// Fallback to old method
			return getPopularTracksFallback(limit);
		}
	}
	
	/**
	 * Fallback method to get popular tracks when chart playlists fail.
	 * Fetches tracks and sorts by playback_count.
	 */
	private List<Map<String, Object>> getPopularTracksFallback(int limit) {
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for popular tracks. User may need to authenticate.");
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Using fallback method to fetch popular tracks");
			String url = soundCloudApiBaseUrl + "/tracks?limit=" + (limit * 3) + "&linked_partitioning=true";
			System.out.println("Fallback URL: " + url);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			// SoundCloud API returns paginated responses as objects with "collection" field, not direct arrays
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url, 
				HttpMethod.GET, 
				request, 
				new ParameterizedTypeReference<Map<String, Object>>(){}
			);
			
			System.out.println("Fallback response status: " + response.getStatusCode());
			Map<String, Object> responseBody = response.getBody();
			if (responseBody == null) {
				System.err.println("Fallback method: No response body from SoundCloud API");
				return new ArrayList<Map<String, Object>>();
			}
			
			// Extract tracks from paginated response
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
			if (tracks == null || tracks.isEmpty()) {
				System.err.println("Fallback method: No tracks in collection from SoundCloud API");
				System.err.println("Response keys: " + responseBody.keySet());
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Fallback method: Retrieved " + tracks.size() + " tracks from SoundCloud");
			
			// Sort by playback_count descending and limit results
			List<Map<String, Object>> filteredTracks = tracks.stream()
				.filter(track -> {
					Object playbackCount = track.get("playback_count");
					return playbackCount != null && ((Number) playbackCount).longValue() > 100;
				})
				.sorted((a, b) -> {
					long countA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
					long countB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
					return Long.compare(countB, countA);
				})
				.limit(limit)
				.collect(java.util.stream.Collectors.toList());
			
			System.out.println("Fallback method: Returning " + filteredTracks.size() + " filtered tracks");
			return filteredTracks;
		} catch (Exception e) {
			System.err.println("Error in fallback method for popular tracks: " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}

	/**
	 * Gets a featured track using an alternative algorithm to avoid overlap with Popular Now.
	 * 
	 * Strategy: Selects from discovery tracks (high engagement, rising tracks) or from
	 * popular tracks positions 11-30 (avoiding top 10 which might overlap with Popular Now).
	 * Does NOT use Genre of the Day tracks to avoid redundancy.
	 * 
	 * @return A featured track or empty map if none available
	 */
	public Map<String, Object> getFeaturedTrack() {
		try {
			// Check if we have a cached track for today
			LocalDate today = LocalDate.now();
			if (cachedSongOfTheDay != null && cachedSongDate != null && cachedSongDate.equals(today)) {
				System.out.println("Returning cached song of the day: " + cachedSongOfTheDay.get("title") + " (cached on " + cachedSongDate + ")");
				return cachedSongOfTheDay;
			}
			
			// Primary Strategy: Discovery tracks (high engagement, rising tracks)
			// These are tracks with good engagement metrics but not necessarily in top charts
			System.out.println("Fetching Song of the Day from discovery tracks...");
			List<Map<String, Object>> discoveryTracks = getDiscoveryTracks(20);
			if (!discoveryTracks.isEmpty()) {
				// Use date-based seed to select a track consistently throughout the day
				long seed = today.toEpochDay();
				Random random = new Random(seed);
				int selectedIndex = random.nextInt(Math.min(discoveryTracks.size(), 10));
				Map<String, Object> selectedTrack = discoveryTracks.get(selectedIndex);
				
				// Fetch lyrics for the track (async/non-blocking)
				try {
					Object userObj = selectedTrack.get("user");
					if (userObj instanceof Map<?, ?> userMap) {
						@SuppressWarnings("unchecked")
						Map<String, Object> userMapTyped = (Map<String, Object>) userMap;
						String artist = (String) userMapTyped.getOrDefault("username", "");
						String title = (String) selectedTrack.getOrDefault("title", "");
						if (!artist.isEmpty() && !title.isEmpty()) {
							String lyrics = lyricsService.getLyrics(artist, title);
							if (lyrics != null && !lyrics.isEmpty()) {
								selectedTrack.put("lyrics", lyrics);
							}
						}
					}
				} catch (Exception e) {
					System.out.println("Error fetching lyrics for featured track: " + e.getMessage());
					// Continue without lyrics - not critical
				}
				
				// Cache the result for today
				cachedSongOfTheDay = selectedTrack;
				cachedSongDate = today;
				
				System.out.println("Song of the day (from discovery): " + selectedTrack.get("title") + " (cached for " + today + ")");
				return selectedTrack;
			}
			
			// Fallback: Use popular tracks positions 11-30 (avoiding top 10 which might overlap with Popular Now)
			System.out.println("Discovery method returned no tracks, using popular tracks 11-30 as fallback...");
			List<Map<String, Object>> popularTracks = getPopularTracks(50);
			if (!popularTracks.isEmpty()) {
				// Select from tracks 11-30 (avoiding top 10 which might overlap with Popular Now)
				long seed = today.toEpochDay();
				Random random = new Random(seed);
				int startIndex = Math.min(10, popularTracks.size() - 1);
				int endIndex = Math.min(30, popularTracks.size());
				if (endIndex > startIndex) {
					int selectedIndex = startIndex + random.nextInt(endIndex - startIndex);
					Map<String, Object> selectedTrack = new HashMap<String, Object>(popularTracks.get(selectedIndex));
					
					// Fetch lyrics for the track (async/non-blocking)
					try {
						Object userObj = selectedTrack.get("user");
						if (userObj instanceof Map<?, ?> userMap) {
							@SuppressWarnings("unchecked")
							Map<String, Object> userMapTyped = (Map<String, Object>) userMap;
							String artist = (String) userMapTyped.getOrDefault("username", "");
							String title = (String) selectedTrack.getOrDefault("title", "");
							if (!artist.isEmpty() && !title.isEmpty()) {
								String lyrics = lyricsService.getLyrics(artist, title);
								if (lyrics != null && !lyrics.isEmpty()) {
									selectedTrack.put("lyrics", lyrics);
								}
							}
						}
					} catch (Exception e) {
						System.out.println("Error fetching lyrics for featured track: " + e.getMessage());
						// Continue without lyrics - not critical
					}
					
					cachedSongOfTheDay = selectedTrack;
					cachedSongDate = today;
					
					System.out.println("Song of the day (from popular tracks 11-30): " + selectedTrack.get("title") + " (cached for " + today + ")");
					return selectedTrack;
				} else if (popularTracks.size() > 0) {
					// If we don't have enough tracks for 11-30 range, use any available track
					int selectedIndex = random.nextInt(popularTracks.size());
					Map<String, Object> selectedTrack = new HashMap<String, Object>(popularTracks.get(selectedIndex));
					
					// Fetch lyrics for the track (async/non-blocking)
					try {
						Object userObj = selectedTrack.get("user");
						if (userObj instanceof Map<?, ?> userMap) {
							@SuppressWarnings("unchecked")
							Map<String, Object> userMapTyped = (Map<String, Object>) userMap;
							String artist = (String) userMapTyped.getOrDefault("username", "");
							String title = (String) selectedTrack.getOrDefault("title", "");
							if (!artist.isEmpty() && !title.isEmpty()) {
								String lyrics = lyricsService.getLyrics(artist, title);
								if (lyrics != null && !lyrics.isEmpty()) {
									selectedTrack.put("lyrics", lyrics);
								}
							}
						}
					} catch (Exception e) {
						System.out.println("Error fetching lyrics for featured track: " + e.getMessage());
						// Continue without lyrics - not critical
					}
					
					cachedSongOfTheDay = selectedTrack;
					cachedSongDate = today;
					
					System.out.println("Song of the day (from popular tracks, limited selection): " + selectedTrack.get("title") + " (cached for " + today + ")");
					return selectedTrack;
				}
			}
		} catch (Exception e) {
			System.out.println("Error fetching featured track: " + e.getMessage());
			e.printStackTrace();
		}
		return new HashMap<String, Object>();
	}
	
	/**
	 * Gets discovery tracks - tracks with high engagement that may not be in top charts.
	 * Fetches tracks and sorts by engagement metrics (likes, reposts, comments) rather than just playback count.
	 * This helps discover rising tracks that aren't necessarily in the top 50 chart.
	 * 
	 * @param limit Maximum number of tracks to return
	 * @return List of discovery tracks sorted by engagement
	 */
	private List<Map<String, Object>> getDiscoveryTracks(int limit) {
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for discovery tracks.");
				return new ArrayList<Map<String, Object>>();
			}
			
			// Fetch a larger set of tracks to find ones with good engagement
			// We'll sort by engagement score rather than just playback count
			String url = soundCloudApiBaseUrl + "/tracks?limit=" + (limit * 5) + 
				"&linked_partitioning=true";
			
			System.out.println("Fetching discovery tracks from: " + url);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>(){}
			);
			
			if (response.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> responseBody = response.getBody();
				if (responseBody == null) {
					System.err.println("Discovery tracks response body is null");
					return new ArrayList<Map<String, Object>>();
				}
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
				
				if (tracks != null && !tracks.isEmpty()) {
					// Filter for tracks with good engagement and sort by engagement score
					// This helps discover tracks that are rising but not necessarily charting
					List<Map<String, Object>> filteredTracks = tracks.stream()
						.filter(track -> {
							// Only include tracks with some engagement
							long likes = ((Number) track.getOrDefault("favoritings_count", 0)).longValue();
							long reposts = ((Number) track.getOrDefault("reposts_count", 0)).longValue();
							long comments = ((Number) track.getOrDefault("comment_count", 0)).longValue();
							long plays = ((Number) track.getOrDefault("playback_count", 0)).longValue();
							
							// Include tracks with decent engagement (at least 1000 plays and some likes/reposts)
							// This filters out very new tracks but includes rising ones
							return plays >= 1000 && (likes > 10 || reposts > 5 || comments > 5);
						})
						.sorted((a, b) -> {
							// Calculate engagement score: likes + reposts*2 + comments*0.5
							// This prioritizes tracks with high engagement relative to plays
							long likesA = ((Number) a.getOrDefault("favoritings_count", 0)).longValue();
							long repostsA = ((Number) a.getOrDefault("reposts_count", 0)).longValue();
							long commentsA = ((Number) a.getOrDefault("comment_count", 0)).longValue();
							long playsA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
							
							long likesB = ((Number) b.getOrDefault("favoritings_count", 0)).longValue();
							long repostsB = ((Number) b.getOrDefault("reposts_count", 0)).longValue();
							long commentsB = ((Number) b.getOrDefault("comment_count", 0)).longValue();
							long playsB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
							
							// Engagement score: weighted sum of interactions
							long scoreA = likesA + repostsA * 2 + commentsA / 2;
							long scoreB = likesB + repostsB * 2 + commentsB / 2;
							
							// Prefer tracks with higher engagement-to-plays ratio (rising tracks)
							// But also consider absolute engagement
							double ratioA = playsA > 0 ? (double) scoreA / playsA : 0;
							double ratioB = playsB > 0 ? (double) scoreB / playsB : 0;
							
							// Sort by engagement ratio first, then by absolute engagement
							int ratioCompare = Double.compare(ratioB, ratioA);
							if (ratioCompare != 0) {
								return ratioCompare;
							}
							return Long.compare(scoreB, scoreA);
						})
						.limit(limit)
						.collect(java.util.stream.Collectors.toList());
					
					System.out.println("Found " + filteredTracks.size() + " discovery tracks with good engagement");
					return filteredTracks;
				}
			}
		} catch (Exception e) {
			System.err.println("Error fetching discovery tracks: " + e.getMessage());
			e.printStackTrace();
		}
		return new ArrayList<Map<String, Object>>();
	}

	/**
	 * Gets a featured artist from trending/popular tracks.
	 * Selects the artist with the highest trending score across their tracks.
	 * 
	 * @return A featured artist or empty map if none available
	 */
	public Map<String, Object> getFeaturedArtist(boolean forceRefresh) {
		System.out.println("getFeaturedArtist() method called (forceRefresh: " + forceRefresh + ")");
		try {
			// If force refresh is requested, clear cache first
			if (forceRefresh) {
				System.out.println("Force refresh requested - clearing artist cache");
				cachedArtistOfTheDay = null;
				cachedArtistDate = null;
			}
			
			// Check if we have a cached artist for today
			LocalDate today = LocalDate.now();
			if (cachedArtistOfTheDay != null && cachedArtistDate != null && cachedArtistDate.equals(today)) {
				// Check if cached description is empty - if so, regenerate
				String cachedDescription = (String) cachedArtistOfTheDay.get("description");
				if (cachedDescription == null || cachedDescription.trim().isEmpty()) {
					System.out.println("Cached artist description is empty - forcing regeneration");
					cachedArtistOfTheDay = null;
					cachedArtistDate = null;
				} else {
					System.out.println("Returning cached artist of the day: " + cachedArtistOfTheDay.get("username") + " (cached on " + cachedArtistDate + ")");
					return cachedArtistOfTheDay;
				}
			}
			
			List<Map<String, Object>> popularTracks = getPopularTracks(50);
			System.out.println("getFeaturedArtist: Retrieved " + popularTracks.size() + " popular tracks");
			if (!popularTracks.isEmpty()) {
				// Extract unique artists with their trending scores
				Map<String, Map<String, Object>> artistMap = new HashMap<String, Map<String, Object>>();
				Map<String, Double> artistTrendingScores = new HashMap<String, Double>();
				
				for (Map<String, Object> track : popularTracks) {
					Object userObj = track.get("user");
					if (userObj instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> user = (Map<String, Object>) userObj;
						String userId = String.valueOf(user.get("id"));
						
						// Get popularity score for this track (from chart playlists)
						double popularityScore = ((Number) track.getOrDefault("_popularity_score", 
							((Number) track.getOrDefault("playback_count", 0)).doubleValue())).doubleValue();
						
						if (!artistMap.containsKey(userId)) {
							artistMap.put(userId, user);
							artistTrendingScores.put(userId, popularityScore);
						} else {
							// Add to total popularity score for this artist
							artistTrendingScores.put(userId, artistTrendingScores.get(userId) + popularityScore);
						}
					}
				}
				
				if (!artistMap.isEmpty()) {
					// Sort artists by trending score
					List<Map.Entry<String, Double>> sortedArtists = artistTrendingScores.entrySet().stream()
						.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
						.collect(java.util.stream.Collectors.toList());
					
					// Use date-based seed for deterministic but daily-changing selection
					// This ensures the same artist is selected throughout the day
					long seed = today.toEpochDay(); // Convert date to a number
					Random random = new Random(seed);
					int selectedIndex = random.nextInt(Math.min(sortedArtists.size(), 10)); // Select from top 10
					String selectedArtistId = sortedArtists.get(selectedIndex).getKey();
					Map<String, Object> selectedArtist = artistMap.get(selectedArtistId);
					
					// Get artist description and popular tracks
					System.out.println("========================================");
					System.out.println("Getting description for Artist of the Day: " + selectedArtist.get("username"));
					System.out.println("========================================");
					String artistDescription = getArtistDescription(selectedArtist);
					System.out.println("Description result: " + (artistDescription != null ? "SUCCESS (length: " + artistDescription.length() + ")" : "NULL"));
					
					// Extract permalink - try multiple fields
					String artistPermalink = null;
					if (selectedArtist.containsKey("permalink") && selectedArtist.get("permalink") != null) {
						artistPermalink = (String) selectedArtist.get("permalink");
					} else if (selectedArtist.containsKey("permalink_url") && selectedArtist.get("permalink_url") != null) {
						String permalinkUrl = (String) selectedArtist.get("permalink_url");
						// Extract permalink from URL (e.g., "https://soundcloud.com/myabandonedhome" -> "myabandonedhome")
						if (permalinkUrl.contains("soundcloud.com/")) {
							String[] parts = permalinkUrl.split("soundcloud.com/");
							if (parts.length > 1) {
								artistPermalink = parts[1].split("/")[0].split("\\?")[0];
							}
						}
					}
					
					// Fallback to username if permalink still not found
					if (artistPermalink == null || artistPermalink.isEmpty()) {
						artistPermalink = (String) selectedArtist.getOrDefault("username", "");
					}
					
					// Ensure permalink_url is in the result for frontend
					if (!selectedArtist.containsKey("permalink_url") || selectedArtist.get("permalink_url") == null) {
						selectedArtist.put("permalink_url", "https://soundcloud.com/" + artistPermalink);
					}
					// Ensure permalink is in the result
					if (!selectedArtist.containsKey("permalink") || selectedArtist.get("permalink") == null) {
						selectedArtist.put("permalink", artistPermalink);
					}
					
					System.out.println("Artist permalink extraction - username: " + selectedArtist.get("username") + ", permalink: " + artistPermalink + ", permalink_url: " + selectedArtist.get("permalink_url"));
					
					// Use the user ID directly from the selected artist object instead of resolving
					Object userIdObj = selectedArtist.get("id");
					String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
					
					System.out.println("Calling getTracksFromArtist for: " + artistPermalink + " (user ID: " + userId + ")");
					
					// Try multiple approaches to get tracks
					List<Map<String, Object>> artistTracks = null;
					
					// Approach 1: Try popular-tracks URL directly using the permalink/username we have
					// Format: soundcloud.com/{permalink}/popular-tracks
					if (artistPermalink != null && !artistPermalink.isEmpty()) {
						System.out.println("Attempting Approach 1: Using popular-tracks URL with permalink: " + artistPermalink);
						artistTracks = getTracksFromPopularTracksUrl(artistPermalink, 5);
						if (artistTracks != null && !artistTracks.isEmpty()) {
							System.out.println("Approach 1 SUCCESS: Found " + artistTracks.size() + " tracks via popular-tracks URL");
						}
					}
					
					// Approach 2: Try using user ID directly
					if ((artistTracks == null || artistTracks.isEmpty()) && userId != null) {
						System.out.println("Attempting Approach 2: Using user ID directly: " + userId);
						artistTracks = getTracksFromArtistByUserId(userId, 5);
						if (artistTracks != null && !artistTracks.isEmpty()) {
							System.out.println("Approach 2 SUCCESS: Found " + artistTracks.size() + " tracks via user ID");
						}
					}
					
					// Approach 3: Fallback to permalink-based method
					if ((artistTracks == null || artistTracks.isEmpty()) && artistPermalink != null && !artistPermalink.isEmpty()) {
						System.out.println("Attempting Approach 3: Using permalink-based method: " + artistPermalink);
						artistTracks = getTracksFromArtist(artistPermalink, 5);
						if (artistTracks != null && !artistTracks.isEmpty()) {
							System.out.println("Approach 3 SUCCESS: Found " + artistTracks.size() + " tracks via permalink");
						}
					}
					
					if (artistTracks == null) {
						artistTracks = new ArrayList<Map<String, Object>>();
					}
					
					System.out.println("Final result: getTracksFromArtist returned " + artistTracks.size() + " tracks");
					
					// Fetch enhanced artist info from TheAudioDB (async/non-blocking)
					try {
						String artistName = (String) selectedArtist.getOrDefault("username", "");
						if (!artistName.isEmpty()) {
							Map<String, Object> enhancedInfo = enhancedArtistService.getEnhancedArtistInfo(artistName);
							if (enhancedInfo != null) {
								// Add enhanced info to result
								selectedArtist.put("enhancedInfo", enhancedInfo);
								
								// Extract high-quality artwork if available
								String artwork = enhancedArtistService.getAlbumArtwork(artistName, null);
								if (artwork != null && !artwork.isEmpty()) {
									selectedArtist.put("highQualityArtwork", artwork);
								}
							}
						}
					} catch (Exception e) {
						System.out.println("Error fetching enhanced artist info: " + e.getMessage());
						// Continue without enhanced info - not critical
					}
					
					// Create result with artist, description, and tracks
					Map<String, Object> result = new HashMap<String, Object>(selectedArtist);
					// Only cache if we have a valid description - don't cache empty descriptions
					String finalDescription = artistDescription != null ? artistDescription : "";
					result.put("description", finalDescription);
					result.put("tracks", artistTracks);
					
					System.out.println("========================================");
					System.out.println("Final Artist of the Day result:");
					System.out.println("  - Username: " + result.get("username"));
					System.out.println("  - Description: " + (finalDescription.isEmpty() ? "EMPTY" : "PRESENT (length: " + finalDescription.length() + ")"));
					if (!finalDescription.isEmpty()) {
						System.out.println("  - Description preview: " + finalDescription.substring(0, Math.min(100, finalDescription.length())) + "...");
					}
					System.out.println("  - Tracks: " + (artistTracks != null ? artistTracks.size() : 0));
					System.out.println("========================================");
					
					// Only cache the result if we have a valid description
					// This prevents caching empty descriptions that would block regeneration
					if (!finalDescription.isEmpty()) {
						cachedArtistOfTheDay = result;
						cachedArtistDate = today;
						System.out.println("✓ Cached artist of the day with valid description");
					} else {
						System.out.println("✗ Not caching artist - description is empty (will regenerate on next request)");
					}
					
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> cachedTracks = (List<Map<String, Object>>) result.get("tracks");
					System.out.println("Artist of the day: " + selectedArtist.get("username") + ", tracks in result: " + (cachedTracks != null ? cachedTracks.size() : 0));
					
					// If tracks are still empty, try fallback method directly
					if (cachedTracks == null || cachedTracks.isEmpty()) {
						System.err.println("WARNING: Artist tracks are empty after getTracksFromArtist, trying fallback directly...");
						String accessToken = getAccessTokenForRequest();
						if (accessToken != null) {
							List<Map<String, Object>> fallbackTracks = getTracksFromArtistFallback(artistPermalink, 5, accessToken);
							if (fallbackTracks != null && !fallbackTracks.isEmpty()) {
								System.out.println("Fallback method returned " + fallbackTracks.size() + " tracks, updating result");
								result.put("tracks", fallbackTracks);
							} else {
								System.err.println("Fallback method also returned 0 tracks");
							}
						} else {
							System.err.println("Cannot use fallback: no access token available");
						}
					}
					
					return result;
				}
			}
		} catch (Exception e) {
			System.err.println("Error fetching featured artist: " + e.getMessage());
			e.printStackTrace();
		}
		return new HashMap<String, Object>();
	}

	/**
	 * Gets a random featured genre and fetches popular tracks from that genre.
	 * Uses SoundCloud's tag-based popular tracks endpoint.
	 * 
	 * @return Map containing genre name, description, and popular tracks from that genre
	 */
	public Map<String, Object> getFeaturedGenreWithTracks() {
		try {
			// Check if we have a cached genre for today
			LocalDate today = LocalDate.now();
			if (cachedGenreOfTheDay != null && cachedGenreDate != null && cachedGenreDate.equals(today)) {
				System.out.println("Returning cached genre of the day: " + cachedGenreOfTheDay.get("genre") + " (cached on " + cachedGenreDate + ")");
				return cachedGenreOfTheDay;
			}
			
			// List of popular genres to choose from
			List<String> popularGenres = Arrays.asList(
				"wave", "hip hop", "pop", "house", "techno", "dubstep",
				"r&b", "folk", "country", "punk", "reggae", "indietronica",
				"future garage", "vaporwave", "synthwave", "drum & bass",
				"rage", "lo-fi", "chillwave", "shoegaze", "post-rock",
				"math rock", "hyperpop", "neo-soul", "afrobeats", "phonk",
				"dancehall", "grime", "drill", "acid jazz", "alternative rock",
				"trip hop", "deep house", "tropical house", "bitpop", "synthpop",
				"indie pop", "progressive house", "progressive metal", "funk",
				"grunge", "melodic death metal", "moombahton", "trance",
				"stoner rock", "new wave", "trap", "cloud rap", "nightcore",
				"witch house", "chopped & screwed", "slowed & reverb", "screamo"
			);
			
			// Use date-based seed for deterministic but daily-changing selection
			// This ensures the same genre is selected throughout the day
			long seed = today.toEpochDay(); // Convert date to a number
			Random random = new Random(seed);
			String selectedGenre = popularGenres.get(random.nextInt(popularGenres.size()));
			
			// Get genre description
			String genreDescription = getGenreDescription(selectedGenre);
			
			// Fetch popular tracks from this genre using tag-based endpoint
			List<Map<String, Object>> genreTracks = getTracksFromGenreTag(selectedGenre, 5);
			
			Map<String, Object> result = new HashMap<String, Object>();
			result.put("genre", selectedGenre);
			// Ensure description is never null - use fallback if generation failed
			result.put("description", genreDescription != null ? genreDescription : 
				selectedGenre.substring(0, 1).toUpperCase() + selectedGenre.substring(1) + " is a diverse and evolving music genre with a rich history and dedicated fanbase.");
			result.put("tracks", genreTracks);
			
			// Cache the result for today
			cachedGenreOfTheDay = result;
			cachedGenreDate = today;
			
			System.out.println("Featured genre of the day: " + selectedGenre + ", tracks: " + genreTracks.size() + " (cached for " + today + ")");
			return result;
		} catch (Exception e) {
			System.err.println("Error fetching featured genre with tracks: " + e.getMessage());
			e.printStackTrace();
			// Return default
			Map<String, Object> result = new HashMap<String, Object>();
			result.put("genre", "electronic");
			result.put("description", "Electronic music encompasses a wide range of genres that primarily use electronic instruments and technology.");
			result.put("tracks", new ArrayList<Map<String, Object>>());
			return result;
		}
	}

	/**
	 * Gets popular tracks from a specific genre using SoundCloud's tag-based endpoint.
	 * 
	 * @param genreName The genre name (e.g., "wave", "electronic")
	 * @param limit Maximum number of tracks to return
	 * @return List of popular tracks from that genre
	 */
	private List<Map<String, Object>> getTracksFromGenreTag(String genreName, int limit) {
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for genre tag request.");
				return new ArrayList<Map<String, Object>>();
			}
			
		// Try using the resolve endpoint first for tag URLs (e.g., soundcloud.com/tags/ambient/popular-tracks)
		// If that fails, fall back to the direct /tracks?tags= endpoint
		String encodedGenre = java.net.URLEncoder.encode(genreName.toLowerCase(), "UTF-8");
		String tagUrl = "https://soundcloud.com/tags/" + encodedGenre + "/popular-tracks";
		
		System.out.println("Fetching tracks from genre tag: " + genreName);
		System.out.println("Encoded genre: " + encodedGenre);
		System.out.println("Tag URL: " + tagUrl);
		
		// First, try to resolve the tag URL
		String url = null;
		try {
			String resolveUrl = soundCloudApiBaseUrl + "/resolve?url=" + java.net.URLEncoder.encode(tagUrl, "UTF-8");
			System.out.println("Attempting to resolve tag URL: " + resolveUrl);
			
			HttpHeaders resolveHeaders = new HttpHeaders();
			resolveHeaders.setBearerAuth(accessToken);
			resolveHeaders.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			resolveHeaders.set("Accept", "application/json");
			HttpEntity<String> resolveRequest = new HttpEntity<String>(resolveHeaders);
			
			ResponseEntity<Map<String, Object>> resolveResponse = restTemplate.exchange(
				resolveUrl,
				HttpMethod.GET,
				resolveRequest,
				new ParameterizedTypeReference<Map<String, Object>>(){}
			);
			
			if (resolveResponse.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> resolved = resolveResponse.getBody();
				if (resolved == null) {
					System.out.println("Resolved tag response body is null");
				} else {
				Object kind = resolved.get("kind");
				if ("playlist".equals(kind) || "system-playlist".equals(kind)) {
					// If it resolves to a playlist, get tracks from the playlist
					Object playlistId = resolved.get("id");
					if (playlistId != null) {
						url = soundCloudApiBaseUrl + "/playlists/" + playlistId + "/tracks?limit=" + (limit * 10) + "&linked_partitioning=true";
						System.out.println("Resolved to playlist, using URL: " + url);
					}
				}
				}
			}
		} catch (Exception e) {
			System.out.println("Could not resolve tag URL, will try direct /tracks?tags= endpoint: " + e.getMessage());
		}
		
		// Fallback to direct /tracks?tags= endpoint if resolve didn't work
		if (url == null) {
			url = soundCloudApiBaseUrl + "/tracks?tags=" + encodedGenre + 
				"&limit=" + (limit * 10) + "&linked_partitioning=true";
			System.out.println("Using direct tracks endpoint: " + url);
		}
			
			try {
				HttpHeaders headers = new HttpHeaders();
				headers.setBearerAuth(accessToken);
				headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
				headers.set("Accept", "application/json");
				HttpEntity<String> request = new HttpEntity<String>(headers);
				
				// SoundCloud returns paginated responses as objects with "collection" field
				ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
					url, 
					HttpMethod.GET, 
					request, 
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
				
				System.out.println("Genre tag response status: " + response.getStatusCode());
				
				if (!response.getStatusCode().is2xxSuccessful()) {
					System.err.println("Genre tag API returned non-2xx status: " + response.getStatusCode());
					return new ArrayList<Map<String, Object>>();
				}
				
				Map<String, Object> responseBody = response.getBody();
				if (responseBody == null) {
					System.err.println("No response body from genre tag endpoint");
					return new ArrayList<Map<String, Object>>();
				}
				
				// Log response body keys for debugging
				System.out.println("Genre tag response body keys: " + responseBody.keySet());
				
				// Extract tracks from paginated response
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
				
				// Log how many tracks were returned
				if (tracks != null) {
					System.out.println("Received " + tracks.size() + " tracks from API for genre: " + genreName);
					if (tracks.size() > 0) {
						// Log first track's tags for debugging
						Map<String, Object> firstTrack = tracks.get(0);
						Object firstTagList = firstTrack.get("tag_list");
						System.out.println("Sample track tags: " + firstTagList);
					}
				} else {
					System.out.println("No tracks in collection (tracks is null)");
				}
				
				if (tracks == null || tracks.isEmpty()) {
					// Try direct list format (some endpoints return arrays directly)
					if (responseBody instanceof List) {
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> directTracks = (List<Map<String, Object>>) responseBody;
						if (directTracks != null && !directTracks.isEmpty()) {
							System.out.println("Found " + directTracks.size() + " tracks in direct list format");
							
							// Filter tracks: English titles only and genre must be in tags
							String genreLower = genreName.toLowerCase();
							// Normalize genre: remove spaces, hyphens, and special characters like "&"
							String genreNormalized = genreLower.replaceAll("[\\s&-]", "").replace("&", "");
							// Create genre aliases for common variations (e.g., "drum & bass" -> "dnb", "drumandbass")
							List<String> genreAliases = getGenreAliases(genreLower);
							List<Map<String, Object>> filteredDirectTracks = new ArrayList<Map<String, Object>>();
							
							for (Map<String, Object> track : directTracks) {
								// Check if title is in English
								Object titleObj = track.get("title");
								if (titleObj == null || !(titleObj instanceof String)) {
									continue;
								}
								String title = (String) titleObj;
								if (!isEnglishTitle(title)) {
									System.out.println("  Filtered out (non-English): " + title);
									continue;
								}
								
								// Check if genre name appears in tag_list
								Object tagListObj = track.get("tag_list");
								if (tagListObj == null || !(tagListObj instanceof String)) {
									System.out.println("  Filtered out (no tags): " + title);
									continue;
								}
								String tagList = ((String) tagListObj).toLowerCase();
								
								// Check if genre name appears in tags (more flexible matching)
								String[] tags = tagList.split(",");
								boolean genreFound = false;
								for (String tag : tags) {
									String normalizedTag = tag.trim().toLowerCase();
									// Normalize tag: remove spaces, hyphens, and special characters
									String tagNormalized = normalizedTag.replaceAll("[\\s&-]", "").replace("&", "");
									
									// Use exact match on normalized strings OR check if tag contains genre name
									// This handles cases like "country music", "country pop", etc.
									if (normalizedTag.equals(genreLower) || tagNormalized.equals(genreNormalized) ||
										normalizedTag.contains(genreLower) || tagNormalized.contains(genreNormalized)) {
										genreFound = true;
										break;
									}
									
									// Also check against genre aliases
									for (String alias : genreAliases) {
										if (normalizedTag.equals(alias) || normalizedTag.contains(alias) ||
											tagNormalized.contains(alias.replaceAll("[\\s&-]", "").replace("&", ""))) {
											genreFound = true;
											break;
										}
									}
									if (genreFound) break;
								}
								
								if (!genreFound) {
									System.out.println("  Filtered out (genre not in tags): " + title + " | tags: " + tagList);
									continue;
								}
								
								System.out.println("  ✓ Included: " + title + " | tags: " + tagList);
								filteredDirectTracks.add(track);
							}
							
							System.out.println("Filtered direct tracks to " + filteredDirectTracks.size() + " with English titles and genre in tags (from " + directTracks.size() + " total)");
							
							return filteredDirectTracks.stream()
								.sorted((a, b) -> {
									long countA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
									long countB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
									return Long.compare(countB, countA);
								})
								.limit(limit)
								.collect(java.util.stream.Collectors.toList());
						}
					}
					System.err.println("No tracks in collection from genre tag endpoint");
					return new ArrayList<Map<String, Object>>();
				}
				
				System.out.println("Found " + tracks.size() + " tracks in collection");
				
				// Filter tracks: English titles only and genre must be in tags
				String genreLower = genreName.toLowerCase();
				// Normalize genre: remove spaces, hyphens, and special characters like "&"
				String genreNormalized = genreLower.replaceAll("[\\s&-]", "").replace("&", "");
				// Create genre aliases for common variations (e.g., "drum & bass" -> "dnb", "drumandbass")
				List<String> genreAliases = getGenreAliases(genreLower);
				List<Map<String, Object>> filteredTracks = new ArrayList<Map<String, Object>>();
				
				for (Map<String, Object> track : tracks) {
					// Check if title is in English
					Object titleObj = track.get("title");
					if (titleObj == null || !(titleObj instanceof String)) {
						continue;
					}
					String title = (String) titleObj;
					if (!isEnglishTitle(title)) {
						System.out.println("  Filtered out (non-English): " + title);
						continue;
					}
					
					// Check if genre name appears in tag_list
					Object tagListObj = track.get("tag_list");
					if (tagListObj == null || !(tagListObj instanceof String)) {
						System.out.println("  Filtered out (no tags): " + title);
						continue;
					}
					String tagList = ((String) tagListObj).toLowerCase();
					
					// Check if genre name appears in tags (more flexible matching)
					String[] tags = tagList.split(",");
					boolean genreFound = false;
					for (String tag : tags) {
						String normalizedTag = tag.trim().toLowerCase();
						// Normalize tag: remove spaces, hyphens, and special characters
						String tagNormalized = normalizedTag.replaceAll("[\\s&-]", "").replace("&", "");
						
						// Use exact match on normalized strings OR check if tag contains genre name
						// This handles cases like "country music", "country pop", etc.
						if (normalizedTag.equals(genreLower) || tagNormalized.equals(genreNormalized) ||
							normalizedTag.contains(genreLower) || tagNormalized.contains(genreNormalized)) {
							genreFound = true;
							break;
						}
						
						// Also check against genre aliases
						for (String alias : genreAliases) {
							if (normalizedTag.equals(alias) || normalizedTag.contains(alias) ||
								tagNormalized.contains(alias.replaceAll("[\\s&-]", "").replace("&", ""))) {
								genreFound = true;
								break;
							}
						}
						if (genreFound) break;
					}
					
					if (!genreFound) {
						System.out.println("  Filtered out (genre not in tags): " + title + " | tags: " + tagList);
						continue;
					}
					
					System.out.println("  ✓ Included: " + title + " | tags: " + tagList);
					filteredTracks.add(track);
				}
				
				System.out.println("Filtered to " + filteredTracks.size() + " tracks with English titles and genre in tags (from " + tracks.size() + " total)");
				
				// Sort by playback_count and limit
				return filteredTracks.stream()
					.sorted((a, b) -> {
						long countA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
						long countB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
						return Long.compare(countB, countA);
					})
					.limit(limit)
					.collect(java.util.stream.Collectors.toList());
			} catch (HttpClientErrorException e) {
				System.err.println("HTTP error for genre " + genreName + ": " + e.getStatusCode() + " - " + e.getMessage());
				return new ArrayList<Map<String, Object>>();
			} catch (Exception e) {
				System.err.println("Error fetching tracks for genre " + genreName + ": " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<Map<String, Object>>();
			}
		} catch (Exception e) {
			System.err.println("Error fetching tracks from genre tag " + genreName + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}

	/**
	 * Get genre aliases for flexible tag matching.
	 * Handles common variations like "drum & bass" -> "dnb", "drumandbass", etc.
	 */
	private List<String> getGenreAliases(String genreName) {
		List<String> aliases = new ArrayList<String>();
		String lower = genreName.toLowerCase();
		
		// Add the original genre name
		aliases.add(lower);
		
		// Handle "drum & bass" variations
		if (lower.contains("drum") && lower.contains("bass")) {
			aliases.add("dnb");
			aliases.add("drumandbass");
			aliases.add("drum n bass");
			aliases.add("jungle"); // Related genre
		}
		
		// Handle "r&b" variations
		if (lower.equals("r&b") || lower.equals("r and b")) {
			aliases.add("rnb");
			aliases.add("randb");
			aliases.add("rhythm and blues");
		}
		
		// Handle "lo-fi" variations
		if (lower.contains("lo-fi") || lower.contains("lofi")) {
			aliases.add("lofi");
			aliases.add("lo fi");
			aliases.add("lowfi");
		}
		
		// Handle "hip-hop" variations
		if (lower.contains("hip") && lower.contains("hop")) {
			aliases.add("hiphop");
			aliases.add("hip hop");
		}
		
		return aliases;
	}

	/**
	 * Checks if a track title is in English.
	 * Uses a simple heuristic: if the title contains mostly ASCII characters, it's likely English.
	 * 
	 * @param title The track title
	 * @return true if title appears to be in English
	 */
	private boolean isEnglishTitle(String title) {
		if (title == null || title.isEmpty()) {
			return false;
		}
		
		// Count ASCII characters (0-127) vs non-ASCII characters
		int asciiCount = 0;
		int nonAsciiCount = 0;
		
		for (char c : title.toCharArray()) {
			if (c <= 127) {
				asciiCount++;
			} else {
				nonAsciiCount++;
			}
		}
		
		// If more than 10% non-ASCII characters, likely not English
		int totalChars = asciiCount + nonAsciiCount;
		if (totalChars == 0) {
			return false;
		}
		
		double nonAsciiRatio = (double) nonAsciiCount / totalChars;
		return nonAsciiRatio < 0.1; // Allow up to 10% non-ASCII (for special characters, emojis, etc.)
	}

	/**
	 * Gets a description for a genre.
	 * 
	 * @param genreName The genre name
	 * @return Description of the genre
	 */
	/**
	 * Gets a description for a music genre using Groq API.
	 * Uses context-aware prompts to ensure descriptions are about the music genre, not unrelated topics.
	 * Falls back to hardcoded descriptions if Groq API fails.
	 * 
	 * @param genreName The name of the genre
	 * @return A description of the genre
	 */
	private String getGenreDescription(String genreName) {
		// PRIORITY ORDER: Groq > Hardcoded > Generic
		
		System.out.println("========================================");
		System.out.println("Getting genre description for: " + genreName);
		System.out.println("========================================");
		
		// 1. Try Groq API first (highest priority) - ensures context-aware descriptions
		String groqDescription = getGroqDescription(genreName, "music genre");
		if (groqDescription != null && !groqDescription.trim().isEmpty()) {
			System.out.println("✓ Successfully got Groq description for genre: " + genreName);
			return groqDescription;
		}

		System.out.println("✗ Groq description failed for genre: " + genreName + ", using generic fallback");

		// Final fallback: generic description
		return genreName.substring(0, 1).toUpperCase() + genreName.substring(1)
				+ " is a diverse and evolving music genre with a rich history and dedicated fanbase.";
	}
	
	/**
	 * Gets a description for an artist based on their profile information.
	 * 
	 * @param artist Artist data from SoundCloud API
	 * @return A description of the artist
	 */
	/**
	 * Gets a description for an artist based on their profile information.
	 * Generates AI-style descriptions similar to genre descriptions.
	 * Returns null if no verified information is available.
	 * 
	 * NEW CONDITIONS: Only generates description if artist has:
	 * - >= 10000 followers, OR
	 * - A Wikipedia entry, OR
	 * - A Google Search About section (indicated by substantial SoundCloud bio >100 chars)
	 * 
	 * @param artist Artist data from SoundCloud API
	 * @return A description of the artist, or null if no verified information is available
	 */
	private String getArtistDescription(Map<String, Object> artist) {
		System.out.println("========================================");
		System.out.println("getArtistDescription called");
		System.out.println("========================================");
		
		String username = (String) artist.getOrDefault("username", "");
		String fullName = (String) artist.getOrDefault("full_name", "");
		Object followersCountObj = artist.get("followers_count");
		long followersCount = followersCountObj instanceof Number ? ((Number) followersCountObj).longValue() : 0;
		Object trackCountObj = artist.get("track_count");
		long trackCount = trackCountObj instanceof Number ? ((Number) trackCountObj).longValue() : 0;
		
		System.out.println("  - Username: " + username);
		System.out.println("  - Full name: " + fullName);
		System.out.println("  - Followers: " + followersCount);
		System.out.println("  - Track count: " + trackCount);
		
		// Determine search terms for Wikipedia check
		// Try username first, then fullName, as username is more likely to match Wikipedia
		String searchTerm = username != null && !username.isEmpty() ? username : 
			(fullName != null && !fullName.isEmpty() ? fullName : null);
		
		System.out.println("  - Search term: " + searchTerm);
		
		// With SerpAPI, Wikipedia, and Google Knowledge Graph, we can try OpenAI for ANY artist
		// SerpAPI can find information about most artists, even if they don't have Wikipedia entries
		// or high follower counts. We'll let OpenAI decide if it can generate a description.
		
		// Optional: Check Wikipedia and Google KG for logging purposes (not required)
		boolean hasWikipediaEntry = false;
		boolean hasGoogleAboutSection = false;
		
		if (searchTerm != null) {
			System.out.println("  - Checking Wikipedia entry for: " + searchTerm);
			hasWikipediaEntry = checkWikipediaEntry(searchTerm);
			System.out.println("  - Has Wikipedia entry: " + hasWikipediaEntry);
			
			System.out.println("  - Checking Google Knowledge Graph for: " + searchTerm);
			hasGoogleAboutSection = checkGoogleKnowledgeGraph(searchTerm);
			System.out.println("  - Has Google KG entry: " + hasGoogleAboutSection);
		} else {
			System.out.println("  - Skipping Wikipedia/Google KG checks (search term is null)");
		}
		
		// Always try OpenAI - let it decide if it can generate a description
		// SerpAPI will provide comprehensive web search results even for obscure artists
		System.out.println("  ✓ Proceeding with OpenAI description generation for artist");
		System.out.println("  - Followers: " + followersCount);
		System.out.println("  - Has Wikipedia: " + hasWikipediaEntry);
		System.out.println("  - Has Google KG: " + hasGoogleAboutSection);
		System.out.println("  - Will use SerpAPI, Wikipedia, and Google KG for research");
		
		// PRIORITY ORDER: Groq API (free, with Wikipedia/Google KG/SerpAPI research)
		// We do NOT fall back to SoundCloud bio - Groq must generate the description
		
		// Use Groq API with Wikipedia, Google Knowledge Graph, and SerpAPI research
		if (searchTerm != null && !searchTerm.trim().isEmpty()) {
			System.out.println("  - Calling getGroqDescription for: " + searchTerm + " (music artist)");
			try {
				String groqDescription = getGroqDescription(searchTerm, "music artist");
				if (groqDescription != null && !groqDescription.trim().isEmpty()) {
					System.out.println("  ✓ Successfully got Groq description (length: " + groqDescription.length() + " chars)");
					System.out.println("  - Preview: " + groqDescription.substring(0, Math.min(100, groqDescription.length())) + "...");
					return groqDescription;
				} else {
					System.out.println("  ✗ Groq description returned null or empty");
					System.out.println("  - This could be due to:");
					System.out.println("    1. API key issues");
					System.out.println("    2. Rate limiting");
					System.out.println("    3. Insufficient information found");
					System.out.println("    4. API error");
				}
			} catch (Exception e) {
				System.out.println("  ✗ Exception during Groq description generation: " + e.getClass().getSimpleName());
				System.out.println("  - Message: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("  ✗ Search term is null or empty, cannot get Groq description");
			System.out.println("  - Username: " + username);
			System.out.println("  - Full name: " + fullName);
		}
		
		// Return null if Groq fails - no fallback to SoundCloud bio
		// This ensures we only show AI-generated descriptions based on verified sources
		System.out.println("  ✗ Returning null - description generation failed");
		return null;
	}
	
	/**
	 * Checks if an artist has a Wikipedia entry using the Wikipedia API.
	 * Uses the search API to find matching articles.
	 * 
	 * @param searchTerm The artist's name to search for
	 * @return true if a Wikipedia entry exists, false otherwise
	 */
	private boolean checkWikipediaEntry(String searchTerm) {
		System.out.println("    [checkWikipediaEntry] Checking for: " + searchTerm);
		try {
			// Wikipedia API endpoint for searching
			String encodedSearch = java.net.URLEncoder.encode(searchTerm, "UTF-8");
			String wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedSearch;
			System.out.println("    [checkWikipediaEntry] URL: " + wikiUrl);
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Agent", "SoundWrapped/1.0 (https://soundwrapped.com; contact@example.com)");
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				wikiUrl,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>() {}
			);
			
			Map<String, Object> responseBody = response.getBody();
			if (responseBody != null) {
				// Check if the response indicates a valid page (not a disambiguation or missing page)
				String type = (String) responseBody.get("type");
				// "standard" means a regular article exists
				// "disambiguation" means multiple pages exist (still counts as having an entry)
				if ("standard".equals(type) || "disambiguation".equals(type)) {
					return true;
				}
			}
		} catch (Exception e) {
			// If Wikipedia API call fails, silently return false
			// This prevents API failures from blocking the description generation
			System.out.println("Wikipedia check failed for '" + searchTerm + "': " + e.getMessage());
		}
		
		return false;
	}
	
	/**
	 * Gets a description from Wikipedia for an artist.
	 * Tries multiple name variations to find the correct Wikipedia page.
	 * Handles cases like "osamason" -> "OsamaSon" by trying various capitalizations.
	 * 
	 * @param searchTerm The artist's name to search for
	 * @return Description from Wikipedia, or null if not found
	 */
	private String getWikipediaDescription(String searchTerm) {
		// Build list of search variations to try
		java.util.List<String> searchVariations = new java.util.ArrayList<String>();
		
		// Add original search term
		searchVariations.add(searchTerm);
		
		// Add title case (first letter uppercase, rest lowercase)
		if (searchTerm.length() > 0) {
			searchVariations.add(searchTerm.substring(0, 1).toUpperCase() + searchTerm.substring(1).toLowerCase());
		}
		
		// Add all lowercase
		searchVariations.add(searchTerm.toLowerCase());
		
		// Add all uppercase
		searchVariations.add(searchTerm.toUpperCase());
		
		// For names that might have mixed case (like "osamason" -> "OsamaSon")
		// Try camelCase variations if the name contains common suffixes
		String lower = searchTerm.toLowerCase();
		if (lower.contains("son")) {
			int sonIndex = lower.indexOf("son");
			if (sonIndex > 0) {
				// Try "OsamaSon" style: capitalize first letter and the "Son" part
				String beforeSon = lower.substring(0, sonIndex);
				String sonPart = lower.substring(sonIndex);
				String camelCase = (beforeSon.length() > 0 ? beforeSon.substring(0, 1).toUpperCase() : "") +
					(beforeSon.length() > 1 ? beforeSon.substring(1) : "") +
					(sonPart.length() > 0 ? sonPart.substring(0, 1).toUpperCase() : "") +
					(sonPart.length() > 1 ? sonPart.substring(1) : "");
				searchVariations.add(camelCase);
			}
		}
		
		// Also try with common suffixes like "rapper", "musician" appended
		// This helps when the Wikipedia page title includes the profession
		searchVariations.add(searchTerm + " (rapper)");
		searchVariations.add(searchTerm + " (musician)");
		if (searchTerm.length() > 0) {
			String titleCase = searchTerm.substring(0, 1).toUpperCase() + searchTerm.substring(1).toLowerCase();
			searchVariations.add(titleCase + " (rapper)");
			searchVariations.add(titleCase + " (musician)");
		}
		
		// Remove duplicates
		java.util.Set<String> uniqueVariations = new java.util.LinkedHashSet<String>(searchVariations);
		
		for (String variation : uniqueVariations) {
			try {
				String encodedSearch = java.net.URLEncoder.encode(variation, "UTF-8");
				String wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedSearch;
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("User-Agent", "SoundWrapped/1.0 (https://soundwrapped.com; contact@example.com)");
				headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				HttpEntity<String> request = new HttpEntity<String>(headers);
				
				ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
					wikiUrl,
					HttpMethod.GET,
					request,
					new ParameterizedTypeReference<Map<String, Object>>() {}
				);
				
				Map<String, Object> responseBody = response.getBody();
				if (responseBody != null) {
					String type = (String) responseBody.get("type");
					// Only use "standard" type (not disambiguation)
					if ("standard".equals(type)) {
						String extract = (String) responseBody.get("extract");
						if (extract != null && extract.length() > 50) {
							// Return the full extract paragraph (Wikipedia's extract is already a concise summary)
							// Only truncate if it's extremely long (over 1000 chars) to prevent UI issues
							if (extract.length() > 1000) {
								// Try to truncate at a sentence boundary near 1000 chars
								int lastPeriod = extract.lastIndexOf('.', 1000);
								if (lastPeriod > 500) {
									return extract.substring(0, lastPeriod + 1);
								}
								// If no good sentence boundary, truncate at 997 chars
								return extract.substring(0, 997) + "...";
							}
							return extract.trim();
						}
					}
				}
			} catch (Exception e) {
				// Continue to next variation if this one fails
				continue;
			}
		}
		
		return null;
	}
	
	/**
	 * Extracts the first two sentences from a text string.
	 * Handles various sentence-ending punctuation (. ! ?) and common abbreviations.
	 * 
	 * @param text The text to extract sentences from
	 * @return The first two sentences, or null if less than two sentences found
	 */
	@SuppressWarnings("unused")
	private String extractFirstTwoSentences(String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		
		// Find sentence boundaries (period, exclamation, question mark followed by space or end of string)
		// Use regex to find sentence endings, but be careful with abbreviations
		java.util.regex.Pattern sentencePattern = java.util.regex.Pattern.compile(
			"([.!?])(\\s+|$)"
		);
		java.util.regex.Matcher matcher = sentencePattern.matcher(text);
		
		int sentenceCount = 0;
		int lastMatchEnd = 0;
		
		while (matcher.find() && sentenceCount < 2) {
			int matchStart = matcher.start();
			int matchEnd = matcher.end();
			
			// Check if this might be an abbreviation (common ones like "Mr.", "Dr.", "U.S.", etc.)
			String beforeMatch = text.substring(Math.max(0, matchStart - 3), matchStart);
			boolean isAbbreviation = beforeMatch.matches(".*\\b(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|U\\.S|U\\.K|etc|vs|e\\.g|i\\.e|a\\.m|p\\.m)\\.");
			
			if (!isAbbreviation) {
				sentenceCount++;
				lastMatchEnd = matchEnd;
			}
		}
		
		// If we found at least two sentences, return them
		if (sentenceCount >= 2) {
			return text.substring(0, lastMatchEnd).trim();
		}
		
		// If we found only one sentence, return it
		if (sentenceCount == 1) {
			return text.substring(0, lastMatchEnd).trim();
		}
		
		// If no clear sentence boundaries found, return the text up to 500 characters
		// (to ensure we get substantial content even if sentence detection fails)
		if (text.length() > 500) {
			// Try to find a good breaking point near 500 chars
			int breakPoint = 500;
			int lastPeriod = text.lastIndexOf('.', breakPoint);
			int lastExclamation = text.lastIndexOf('!', breakPoint);
			int lastQuestion = text.lastIndexOf('?', breakPoint);
			int lastSentenceEnd = Math.max(Math.max(lastPeriod, lastExclamation), lastQuestion);
			
			if (lastSentenceEnd > 200) {
				return text.substring(0, lastSentenceEnd + 1).trim();
			}
			return text.substring(0, 497).trim() + "...";
		}
		
		return text.trim();
	}
	
	/**
	 * Checks if an entity has a Google Knowledge Graph entry (About section).
	 * Uses the Google Knowledge Graph Search API.
	 * 
	 * @param searchTerm The entity name to search for (artist name or genre name)
	 * @return true if a Knowledge Graph entry exists with detailed description, false otherwise
	 */
	private boolean checkGoogleKnowledgeGraph(String searchTerm) {
		System.out.println("    [checkGoogleKnowledgeGraph] Checking for: " + searchTerm);
		try {
			// Google Knowledge Graph Search API endpoint
			String encodedQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
			String kgUrl = "https://kgsearch.googleapis.com/v1/entities:search" +
				"?query=" + encodedQuery +
				"&key=" + googleKnowledgeGraphApiKey +
				"&limit=1" +
				"&indent=true";
			System.out.println("    [checkGoogleKnowledgeGraph] URL: " + kgUrl.replace(googleKnowledgeGraphApiKey, "***"));
			
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				kgUrl,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>() {}
			);
			
			Map<String, Object> responseBody = response.getBody();
			if (responseBody != null) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> itemList = (List<Map<String, Object>>) responseBody.get("itemListElement");
				if (itemList != null && !itemList.isEmpty()) {
					// Check if the first result has a detailed description (About section)
					Map<String, Object> firstItem = itemList.get(0);
					@SuppressWarnings("unchecked")
					Map<String, Object> result = (Map<String, Object>) firstItem.get("result");
					if (result != null) {
						// Check for detailedDescription or description field
						@SuppressWarnings("unchecked")
						Map<String, Object> detailedDescription = (Map<String, Object>) result.get("detailedDescription");
						if (detailedDescription != null) {
							String articleBody = (String) detailedDescription.get("articleBody");
							if (articleBody != null && articleBody.length() > 50) {
								return true;
							}
						}
						// Also check for description field
						String description = (String) result.get("description");
						if (description != null && description.length() > 50) {
							return true;
						}
					}
				}
			}
		} catch (Exception e) {
			// If Google Knowledge Graph API call fails, silently return false
			// This prevents API failures from blocking the description generation
			System.out.println("Google Knowledge Graph check failed for '" + searchTerm + "': " + e.getMessage());
		}
		
		return false;
	}
	
	/**
	 * Gets a description from Google Knowledge Graph for an entity (artist or genre).
	 * 
	 * @param searchTerm The entity name to search for
	 * @return Description from Knowledge Graph, or null if not found
	 */
	private String getGoogleKnowledgeGraphDescription(String searchTerm) {
		try {
			// Google Knowledge Graph Search API endpoint
			String encodedQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
			String kgUrl = "https://kgsearch.googleapis.com/v1/entities:search" +
				"?query=" + encodedQuery +
				"&key=" + googleKnowledgeGraphApiKey +
				"&limit=1" +
				"&indent=true";
			
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				kgUrl,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>() {}
			);
			
			Map<String, Object> responseBody = response.getBody();
			if (responseBody != null) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> itemList = (List<Map<String, Object>>) responseBody.get("itemListElement");
				if (itemList != null && !itemList.isEmpty()) {
					Map<String, Object> firstItem = itemList.get(0);
					@SuppressWarnings("unchecked")
					Map<String, Object> result = (Map<String, Object>) firstItem.get("result");
					if (result != null) {
						// Try to get detailedDescription first (more comprehensive)
						@SuppressWarnings("unchecked")
						Map<String, Object> detailedDescription = (Map<String, Object>) result.get("detailedDescription");
						if (detailedDescription != null) {
							String articleBody = (String) detailedDescription.get("articleBody");
							if (articleBody != null && articleBody.length() > 50) {
								// Return the full articleBody paragraph
								// Only truncate if it's extremely long (over 1000 chars) to prevent UI issues
								if (articleBody.length() > 1000) {
									// Try to truncate at a sentence boundary near 1000 chars
									int lastPeriod = articleBody.lastIndexOf('.', 1000);
									if (lastPeriod > 500) {
										return articleBody.substring(0, lastPeriod + 1);
									}
									// If no good sentence boundary, truncate at 997 chars
									return articleBody.substring(0, 997) + "...";
								}
								return articleBody.trim();
							}
						}
						// Fallback to description field
						String description = (String) result.get("description");
						if (description != null && description.length() > 50) {
							// Return the full description
							return description.trim();
						}
					}
				}
			}
		} catch (Exception e) {
			// If Google Knowledge Graph API call fails, return null
			System.out.println("Google Knowledge Graph description fetch failed for '" + searchTerm + "': " + e.getMessage());
		}
		
		return null;
	}
	
	/**
	 * Searches the web using SerpAPI for comprehensive information about an entity.
	 * Extracts knowledge graph, answer box, organic results, and related questions.
	 * 
	 * @param searchTerm The search term (with "music artist" or "music genre" appended)
	 * @return Formatted search results combining knowledge graph, answer box, and top organic results, or null if not found
	 */
	private String getSerpAPIDescription(String searchTerm) {
		System.out.println("========================================");
		System.out.println("SerpAPI search called for: " + searchTerm);
		System.out.println("========================================");
		
		// Check if API key is configured
		if (serpApiKey == null || serpApiKey.trim().isEmpty()) {
			// Try to get from system property or environment variable
			String systemPropKey = System.getProperty("SERPAPI_API_KEY");
			if (systemPropKey != null && !systemPropKey.trim().isEmpty()) {
				serpApiKey = systemPropKey;
				System.out.println("  - SerpAPI key loaded from system property");
			} else {
				String envKey = System.getenv("SERPAPI_API_KEY");
				if (envKey != null && !envKey.trim().isEmpty()) {
					serpApiKey = envKey;
					System.out.println("  - SerpAPI key loaded from environment variable");
				}
			}
		}
		
		if (serpApiKey == null || serpApiKey.trim().isEmpty()) {
			System.err.println("✗ SerpAPI key is not configured");
			return null;
		}
		
		try {
			String encodedQuery = java.net.URLEncoder.encode(searchTerm, "UTF-8");
			String serpApiUrl = "https://serpapi.com/search.json" +
				"?q=" + encodedQuery +
				"&api_key=" + serpApiKey +
				"&engine=google" +
				"&num=5"; // Get top 5 organic results
			
			System.out.println("  - SerpAPI URL: " + serpApiUrl.replace(serpApiKey, "***"));
			
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				serpApiUrl,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>() {}
			);
			
			System.out.println("  - SerpAPI response status: " + response.getStatusCode());
			
			Map<String, Object> responseBody = response.getBody();
			if (responseBody == null) {
				System.out.println("  ✗ SerpAPI response body is null");
				return null;
			}
			
			StringBuilder result = new StringBuilder();
			
			// Extract Knowledge Graph (if available)
			@SuppressWarnings("unchecked")
			Map<String, Object> knowledgeGraph = (Map<String, Object>) responseBody.get("knowledge_graph");
			if (knowledgeGraph != null) {
				String description = (String) knowledgeGraph.get("description");
				if (description != null && !description.trim().isEmpty()) {
					result.append("Knowledge Graph: ").append(description).append("\n\n");
					System.out.println("  ✓ Found Knowledge Graph description");
				}
				
				// Get extended description if available
				@SuppressWarnings("unchecked")
				Map<String, Object> descriptionSource = (Map<String, Object>) knowledgeGraph.get("description_source");
				if (descriptionSource != null) {
					String link = (String) descriptionSource.get("link");
					if (link != null) {
						result.append("Source: ").append(link).append("\n\n");
					}
				}
			}
			
			// Extract Answer Box (if available)
			@SuppressWarnings("unchecked")
			Map<String, Object> answerBox = (Map<String, Object>) responseBody.get("answer_box");
			if (answerBox != null) {
				String answer = (String) answerBox.get("answer");
				if (answer != null && !answer.trim().isEmpty()) {
					result.append("Quick Facts: ").append(answer).append("\n\n");
					System.out.println("  ✓ Found Answer Box");
				}
				
				String snippet = (String) answerBox.get("snippet");
				if (snippet != null && !snippet.trim().isEmpty()) {
					result.append("Details: ").append(snippet).append("\n\n");
				}
			}
			
			// Extract top organic results (snippets)
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> organicResults = (List<Map<String, Object>>) responseBody.get("organic_results");
			if (organicResults != null && !organicResults.isEmpty()) {
				result.append("Additional Information:\n");
				int count = Math.min(3, organicResults.size()); // Get top 3 results
				for (int i = 0; i < count; i++) {
					Map<String, Object> resultItem = organicResults.get(i);
					String snippet = (String) resultItem.get("snippet");
					if (snippet != null && !snippet.trim().isEmpty()) {
						result.append("- ").append(snippet).append("\n");
					}
				}
				System.out.println("  ✓ Found " + count + " organic results");
			}
			
			// Extract related questions if available
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> relatedQuestions = (List<Map<String, Object>>) responseBody.get("related_questions");
			if (relatedQuestions != null && !relatedQuestions.isEmpty()) {
				result.append("\nRelated Questions:\n");
				int count = Math.min(2, relatedQuestions.size()); // Get top 2 questions
				for (int i = 0; i < count; i++) {
					Map<String, Object> question = relatedQuestions.get(i);
					String questionText = (String) question.get("question");
					String questionSnippet = (String) question.get("snippet");
					if (questionText != null && !questionText.trim().isEmpty()) {
						result.append("Q: ").append(questionText);
						if (questionSnippet != null && !questionSnippet.trim().isEmpty()) {
							result.append("\nA: ").append(questionSnippet);
						}
						result.append("\n");
					}
				}
				System.out.println("  ✓ Found " + count + " related questions");
			}
			
			String finalResult = result.toString().trim();
			if (finalResult.isEmpty()) {
				System.out.println("  ✗ No information extracted from SerpAPI response");
				return null;
			}
			
			System.out.println("  ✓ SerpAPI search successful (result length: " + finalResult.length() + " chars)");
			return finalResult;
			
		} catch (HttpClientErrorException e) {
			System.err.println("  ✗ HTTP ERROR calling SerpAPI for '" + searchTerm + "'");
			System.err.println("Status Code: " + e.getStatusCode());
			System.err.println("Response Body: " + e.getResponseBodyAsString());
			return null;
		} catch (Exception e) {
			System.err.println("  ✗ Exception calling SerpAPI for '" + searchTerm + "': " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Gets a description from Groq API (free tier) using research-first approach.
	 * 
	 * This method:
	 * 1. First conducts research using Wikipedia, Google Knowledge Graph, and SerpAPI
	 * 2. Collects all research results
	 * 3. Sends research results to Groq with a prompt to generate a description
	 * 4. Returns the generated description
	 * 
	 * Groq is free to use and provides fast inference. This approach ensures we have
	 * verified information before asking Groq to generate the description.
	 * 
	 * @param entityName The name of the entity (artist or genre)
	 * @param entityType The type of entity: "music genre" or "music artist"
	 * @return Description from Groq, or null if not found or API call fails
	 */
	@org.springframework.cache.annotation.Cacheable(value = "groqDescriptions", key = "#entityName.toLowerCase() + '|' + #entityType.toLowerCase()", unless = "#result == null || #result.isEmpty()")
	private String getGroqDescription(String entityName, String entityType) {
		// Force immediate output
		System.out.flush();
		System.err.flush();
		
		System.out.println("========================================");
		System.out.println("getGroqDescription called for: " + entityName + " (" + entityType + ")");
		System.out.flush();
		System.err.println("========================================");
		System.err.println("getGroqDescription called for: " + entityName + " (" + entityType + ")");
		System.err.println("========================================");
		System.err.flush();
		
		// Use Groq API (free and fast)
		System.out.println("  [GROQ] Using Groq API (free tier, fast inference)");
		
		// Diagnostic: Check Groq API key status
		System.out.println("  [DIAGNOSTIC] Initial Groq API key check:");
		System.out.println("  - @Value injected groqApiKey: " + (this.groqApiKey == null || this.groqApiKey.trim().isEmpty() ? "NULL/EMPTY" : "PRESENT (length: " + this.groqApiKey.length() + ")"));
		System.out.println("  - System property GROQ_API_KEY: " + (System.getProperty("GROQ_API_KEY") != null ? "PRESENT" : "NULL"));
		System.out.println("  - Environment variable GROQ_API_KEY: " + (System.getenv("GROQ_API_KEY") != null ? "PRESENT" : "NULL"));
		System.out.flush();
		
		// Check if Groq API key is loaded - try multiple sources
		String apiKey = groqApiKey;
		if (apiKey == null || apiKey.trim().isEmpty()) {
			// Try to get from system property as fallback (set by Dotenv)
			String systemPropKey = System.getProperty("GROQ_API_KEY");
			if (systemPropKey != null && !systemPropKey.trim().isEmpty()) {
				apiKey = systemPropKey;
				System.out.println("  - Groq API key loaded from system property");
			} else {
				// Try environment variable as last resort
				String envKey = System.getenv("GROQ_API_KEY");
				if (envKey != null && !envKey.trim().isEmpty()) {
					apiKey = envKey;
					System.out.println("  - Groq API key loaded from environment variable");
				}
			}
		}
		
		if (apiKey == null || apiKey.trim().isEmpty()) {
			System.out.println("✗ Groq API key is not configured");
			System.out.println("  - Checked @Value injection: " + (this.groqApiKey == null || this.groqApiKey.trim().isEmpty() ? "not found" : "found"));
			System.out.println("  - Checked system property GROQ_API_KEY: " + (System.getProperty("GROQ_API_KEY") != null ? "found" : "not found"));
			System.out.println("  - Checked environment variable GROQ_API_KEY: " + (System.getenv("GROQ_API_KEY") != null ? "found" : "not found"));
			System.out.println("  - Please ensure GROQ_API_KEY is set in .env file or as environment variable");
			System.err.println("✗ Groq API key is not configured");
			System.err.println("  - Checked @Value injection: " + (this.groqApiKey == null || this.groqApiKey.trim().isEmpty() ? "not found" : "found"));
			System.err.println("  - Checked system property GROQ_API_KEY: " + (System.getProperty("GROQ_API_KEY") != null ? "found" : "not found"));
			System.err.println("  - Checked environment variable GROQ_API_KEY: " + (System.getenv("GROQ_API_KEY") != null ? "found" : "not found"));
			System.err.println("  - Please ensure GROQ_API_KEY is set in .env file or as environment variable");
			return null;
		}
		System.out.println("  - Groq API key configured (length: " + apiKey.length() + ", starts with: " + apiKey.substring(0, Math.min(7, apiKey.length())) + "...)");
		System.err.println("  - Groq API key configured (length: " + apiKey.length() + ", starts with: " + apiKey.substring(0, Math.min(7, apiKey.length())) + "...)");
		
		// Get Groq base URL (defaults to OpenAI-compatible endpoint)
		String baseUrl = groqBaseUrl != null && !groqBaseUrl.trim().isEmpty() ? groqBaseUrl : "https://api.groq.com/openai/v1";
		System.out.println("  - Groq base URL: " + baseUrl);
		
		// Declare apiUrl outside try block so it's accessible in catch block
		String apiUrl = null;
		
		try {
			// Step 1: Conduct research first (Wikipedia, Google KG, SerpAPI)
			System.out.println("  [STEP 1] Conducting research for " + entityType + ": " + entityName);
			
			String searchTerm = entityName + " " + entityType;
			StringBuilder researchContext = new StringBuilder();
			
			// Get Wikipedia information
			System.out.println("  - Searching Wikipedia...");
			String wikiInfo = getWikipediaDescription(searchTerm);
			if (wikiInfo != null && !wikiInfo.trim().isEmpty()) {
				researchContext.append("Wikipedia Information:\n").append(wikiInfo).append("\n\n");
				System.out.println("  ✓ Found Wikipedia information (length: " + wikiInfo.length() + " chars)");
			} else {
				System.out.println("  ✗ No Wikipedia information found");
			}
			
			// Get Google Knowledge Graph information
			System.out.println("  - Searching Google Knowledge Graph...");
			String googleKGInfo = getGoogleKnowledgeGraphDescription(searchTerm);
			if (googleKGInfo != null && !googleKGInfo.trim().isEmpty()) {
				researchContext.append("Google Knowledge Graph Information:\n").append(googleKGInfo).append("\n\n");
				System.out.println("  ✓ Found Google KG information (length: " + googleKGInfo.length() + " chars)");
			} else {
				System.out.println("  ✗ No Google Knowledge Graph information found");
			}
			
			// Get SerpAPI web search information
			System.out.println("  - Searching web via SerpAPI...");
			String serpApiInfo = getSerpAPIDescription(searchTerm);
			if (serpApiInfo != null && !serpApiInfo.trim().isEmpty()) {
				researchContext.append("Web Search Information:\n").append(serpApiInfo).append("\n\n");
				System.out.println("  ✓ Found SerpAPI information (length: " + serpApiInfo.length() + " chars)");
			} else {
				System.out.println("  ✗ No SerpAPI information found");
			}
			
			// Check if we have any research data
			if (researchContext.length() == 0) {
				System.out.println("  ✗ No research data found from any source");
				return null;
			}
			
			// Step 2: Build prompt with research context
			System.out.println("  [STEP 2] Building prompt with research context (total length: " + researchContext.length() + " chars)");
			String prompt;
			if ("music genre".equals(entityType)) {
				prompt = String.format(
					"Based on the following research information, write a concise, informative paragraph (approximately 2-3 sentences, 50-100 words) about the music genre '%s'. " +
					"Focus specifically on the MUSIC GENRE, not any other meaning of the word. " +
					"Include: the genre's origins and history, key characteristics and sound, notable artists or subgenres, " +
					"and its influence on modern music. Write in an engaging, informative style suitable for a music discovery platform. " +
					"Write exactly one paragraph - do not use multiple paragraphs. Keep it concise and to the point.\n\n" +
					"Research Information:\n%s",
					entityName, researchContext.toString()
				);
			} else if ("music artist".equals(entityType)) {
				prompt = String.format(
					"Based on the following research information, write a concise, informative paragraph (approximately 2-3 sentences, 50-100 words) about the music artist '%s'. " +
					"Include: their background and career, musical style and genre, notable works or achievements, " +
					"and their influence on music. Write in an engaging, informative style suitable for a music discovery platform. " +
					"Write exactly one paragraph - do not use multiple paragraphs. Keep it concise and to the point.\n\n" +
					"Research Information:\n%s",
					entityName, researchContext.toString()
				);
			} else {
				System.err.println("Unknown entity type: " + entityType);
				return null;
			}
			
			// Step 3: Call Groq API with research context
			apiUrl = baseUrl + "/chat/completions";
			
			System.out.println("  [STEP 3] Calling Groq API to generate description");
			System.out.println("  - Groq API URL: " + apiUrl);
			System.out.println("  - API Key (first 10 chars): " + (apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "N/A"));
			
			// Build request body for Groq Chat API (simple chat completion, no function calling)
			// Using llama-3.3-70b-versatile (llama-3.1-70b-versatile was decommissioned)
			Map<String, Object> requestBody = new HashMap<String, Object>();
			requestBody.put("model", "llama-3.3-70b-versatile");
			List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
			Map<String, Object> userMessage = new HashMap<String, Object>();
			userMessage.put("role", "user");
			userMessage.put("content", prompt);
			messages.add(userMessage);
			requestBody.put("messages", messages);
			requestBody.put("temperature", 0.7);
			requestBody.put("max_tokens", 150); // Reduced for shorter descriptions (50-100 words)
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(apiKey);
			HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(requestBody, headers);
			
			System.out.println("  - Making HTTP POST request to Groq...");
			System.out.flush();
			
			ResponseEntity<Map<String, Object>> response;
			try {
				response = restTemplate.exchange(
					apiUrl,
					HttpMethod.POST,
					request,
					new ParameterizedTypeReference<Map<String, Object>>() {}
				);
				System.out.println("  - Groq API call completed successfully");
				System.out.flush();
				} catch (Exception apiException) {
					System.out.flush();
					System.err.flush();
					System.out.println("  ✗ EXCEPTION DURING API CALL:");
					System.out.println("  - Exception type: " + apiException.getClass().getName());
					System.out.println("  - Exception message: " + apiException.getMessage());
					if (apiException instanceof org.springframework.web.client.HttpClientErrorException) {
						org.springframework.web.client.HttpClientErrorException httpEx = (org.springframework.web.client.HttpClientErrorException) apiException;
						System.out.println("  - HTTP Status: " + httpEx.getStatusCode());
						String responseBody = httpEx.getResponseBodyAsString();
						System.out.println("  - Response Body: " + responseBody);
						System.err.println("  ✗ HTTP ERROR: " + httpEx.getStatusCode() + " - " + responseBody);
					}
					apiException.printStackTrace();
					System.out.flush();
					System.err.flush();
					throw apiException; // Re-throw to be caught by outer catch blocks
				}
				
				System.out.println("  - Groq API response status: " + response.getStatusCode());
				
				Map<String, Object> responseBody = response.getBody();
				if (responseBody == null) {
					System.out.println("  ✗ Groq response body is null");
					return null;
				}
				
				// Check for errors
				if (responseBody.containsKey("error")) {
					@SuppressWarnings("unchecked")
					Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
					System.err.println("  ✗ Groq API error: " + error);
					return null;
				}
				
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
				if (choices == null || choices.isEmpty()) {
					System.out.println("  ✗ No choices in Groq response");
					return null;
				}
				
				Map<String, Object> firstChoice = choices.get(0);
				
				// Check finish_reason
				Object finishReason = firstChoice.get("finish_reason");
				if (finishReason != null) {
					System.out.println("  - Finish reason: " + finishReason);
					if ("content_filter".equals(finishReason)) {
						System.err.println("  ✗ Response was filtered by content filter");
						return null;
					}
				}
				
				@SuppressWarnings("unchecked")
				Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
				if (message == null) {
					System.out.println("  ✗ No message in Groq response choice");
					return null;
				}
				
				// Get the content from the message
				Object contentObj = message.get("content");
				String text = null;
				
				System.out.println("  - Checking response content from Groq...");
				System.out.println("  - Content object type: " + (contentObj != null ? contentObj.getClass().getName() : "null"));
				
				// Handle content
				if (contentObj != null) {
					if (contentObj instanceof String) {
						text = (String) contentObj;
						System.out.println("  - Content is string, length: " + (text != null ? text.length() : 0));
						if (text != null && text.length() > 0) {
							System.out.println("  - Content preview: " + text.substring(0, Math.min(200, text.length())) + "...");
						}
					} else {
						System.out.println("  - Content is not a string, converting...");
						text = contentObj.toString();
					}
				} else {
					System.out.println("  - Content object is null");
				}
				
				if (text != null && text.trim().length() > 20) {
					String trimmed = text.trim().toLowerCase();
					// Check if Groq indicates it doesn't have information
					if (trimmed.contains("i don't have") || trimmed.contains("i don't know") || 
						trimmed.contains("not enough information") || trimmed.contains("unable to find") ||
						trimmed.contains("no specific information") || trimmed.contains("limited information") ||
						trimmed.contains("i cannot provide") || trimmed.contains("i'm unable to")) {
						System.out.println("  ✗ Groq indicated no information available for '" + entityName + "'");
						return null;
					}
					
					String originalText = text.trim();
					if (originalText.length() > 1000) {
						int lastPeriod = originalText.lastIndexOf('.', 1000);
						if (lastPeriod > 500) {
							return originalText.substring(0, lastPeriod + 1);
						}
						return originalText.substring(0, 997) + "...";
					}
					
					System.out.println("  ✓ Successfully got Groq description (length: " + originalText.length() + " chars)");
					System.out.println("  - Preview: " + originalText.substring(0, Math.min(150, originalText.length())) + "...");
					return originalText;
				} else {
					if (text == null) {
						System.out.println("  ✗ Groq response text is null");
					} else {
						System.out.println("  ✗ Groq response text is too short (length: " + text.trim().length() + ", minimum: 20)");
						System.out.println("  - Text content: '" + text + "'");
					}
					return null;
				}
		} catch (HttpClientErrorException e) {
			// Force flush to ensure error messages are visible
			System.out.flush();
			System.err.flush();
			
			System.out.println("========================================");
			System.out.println("✗ HTTP ERROR calling Groq API for '" + entityName + "'");
			System.out.println("========================================");
			System.out.println("Status Code: " + e.getStatusCode());
			System.out.println("Status Text: " + e.getStatusText());
			System.out.println("Message: " + e.getMessage());
			System.out.flush();
			
			System.err.println("========================================");
			System.err.println("✗ HTTP ERROR calling Groq API for '" + entityName + "'");
			System.err.println("========================================");
			System.err.println("Status Code: " + e.getStatusCode());
			System.err.println("Status Text: " + e.getStatusText());
			System.err.println("Message: " + e.getMessage());
			System.err.flush();
			if (e.getResponseBodyAsString() != null) {
				String responseBody = e.getResponseBodyAsString();
				System.out.println("Response Body: " + responseBody);
				System.err.println("Response Body: " + responseBody);
				
				// Check for common API key errors
				if (responseBody.contains("API key not valid") || responseBody.contains("invalid API key") || 
					responseBody.contains("Incorrect API key") || responseBody.contains("Invalid API key")) {
					System.out.println("");
					System.out.println("⚠️  API KEY ERROR DETECTED!");
					System.out.println("The Groq API key appears to be invalid or not properly configured.");
					System.out.println("Please check:");
					System.out.println("1. The API key is correct in your .env file");
					System.out.println("2. The Groq API key is valid (get one at https://console.groq.com/api-keys)");
					System.out.println("3. The API key has the necessary permissions");
					System.out.println("4. Generate a new API key if needed");
					System.out.println("");
					System.err.println("");
					System.err.println("⚠️  API KEY ERROR DETECTED!");
					System.err.println("The Groq API key appears to be invalid or not properly configured.");
					System.err.println("Please check:");
					System.err.println("1. The API key is correct in your .env file");
					System.err.println("2. The Groq API key is valid (get one at https://console.groq.com/api-keys)");
					System.err.println("3. The API key has the necessary permissions");
					System.err.println("4. Generate a new API key if needed");
					System.err.println("");
				}
			}
			if (apiUrl != null) {
				System.err.println("Request URL: " + apiUrl);
			} else {
				System.err.println("Request URL: (not set - error occurred before URL construction)");
			}
			System.err.println("Full Stack Trace:");
			e.printStackTrace();
			System.err.println("========================================");
		} catch (org.springframework.web.client.RestClientException e) {
			// Catch RestClientException (parent of HttpClientErrorException)
			System.out.println("========================================");
			System.out.println("✗ REST CLIENT EXCEPTION calling Groq API for '" + entityName + "'");
			System.out.println("========================================");
			System.out.println("Exception Type: " + e.getClass().getName());
			System.out.println("Message: " + e.getMessage());
			System.out.println("Full Stack Trace:");
			e.printStackTrace();
			System.out.println("========================================");
			System.err.println("========================================");
			System.err.println("✗ REST CLIENT EXCEPTION calling Groq API for '" + entityName + "'");
			System.err.println("========================================");
			System.err.println("Exception Type: " + e.getClass().getName());
			System.err.println("Message: " + e.getMessage());
			System.err.println("Full Stack Trace:");
			e.printStackTrace();
			System.err.println("========================================");
		} catch (Exception e) {
			System.out.println("========================================");
			System.out.println("✗ GENERAL EXCEPTION calling Groq API for '" + entityName + "'");
			System.out.println("========================================");
			System.out.println("Exception Type: " + e.getClass().getName());
			System.out.println("Message: " + e.getMessage());
			System.out.println("Full Stack Trace:");
			e.printStackTrace();
			System.out.println("========================================");
			System.err.println("========================================");
			System.err.println("✗ GENERAL EXCEPTION calling Groq API for '" + entityName + "'");
			System.err.println("========================================");
			System.err.println("Exception Type: " + e.getClass().getName());
			System.err.println("Message: " + e.getMessage());
			System.err.println("Full Stack Trace:");
			e.printStackTrace();
			System.err.println("========================================");
		}
		
		System.err.println("========================================");
		System.err.println("✗ Returning null - Groq description generation failed for: " + entityName);
		System.err.println("========================================");
		return null;
	}
	
	/**
	 * Gets popular tracks from a specific artist by resolving their popular-tracks URL.
	 * Uses the format: soundcloud.com/{artist}/popular-tracks
	 * 
	 * @param artistPermalink The artist's permalink (username or custom URL)
	 * @param limit Maximum number of tracks to return
	 * @return List of popular tracks from that artist
	 */
	private List<Map<String, Object>> getTracksFromArtist(String artistPermalink, int limit) {
		System.out.println("========================================");
		System.out.println("getTracksFromArtist() called with permalink: " + artistPermalink + ", limit: " + limit);
		System.out.println("========================================");
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for artist tracks request.");
				return new ArrayList<Map<String, Object>>();
			}
			System.out.println("Access token obtained (length: " + (accessToken != null ? accessToken.length() : 0) + ")");
			
			System.out.println("Fetching tracks from artist: " + artistPermalink);
			
			if (artistPermalink == null || artistPermalink.isEmpty()) {
				System.err.println("Artist permalink is null or empty, cannot fetch tracks");
				return new ArrayList<Map<String, Object>>();
			}
			
		// Try to get tracks from popular-tracks URL first
		System.out.println("Attempting to fetch tracks from popular-tracks URL...");
		List<Map<String, Object>> popularTracks = getTracksFromPopularTracksUrl(artistPermalink, limit);
		
		if (popularTracks != null && !popularTracks.isEmpty()) {
			System.out.println("Successfully fetched " + popularTracks.size() + " tracks from popular-tracks URL");
			return popularTracks;
		}
		
		// If popular-tracks URL fails, use fallback but sort by popularity
		System.out.println("Popular-tracks URL failed, using fallback method...");
		return getTracksFromArtistFallback(artistPermalink, limit, accessToken);
			
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromArtist for: " + artistPermalink);
			System.err.println("Error: " + e.getMessage());
			System.err.println("Exception type: " + e.getClass().getName());
			System.err.println("========================================");
			e.printStackTrace();
			System.err.println("Attempting fallback method after exception...");
			try {
				String accessToken = getAccessTokenForRequest();
				if (accessToken != null) {
					return getTracksFromArtistFallback(artistPermalink, limit, accessToken);
				}
			} catch (Exception e2) {
				System.err.println("Fallback also failed: " + e2.getMessage());
				e2.printStackTrace();
			}
			return new ArrayList<Map<String, Object>>();
		}
	}
	
	/**
	 * Gets tracks from an artist by resolving their popular-tracks URL directly.
	 * Format: soundcloud.com/{permalink}/popular-tracks
	 */
	private List<Map<String, Object>> getTracksFromPopularTracksUrl(String artistPermalink, int limit) {
		System.out.println("========================================");
		System.out.println("getTracksFromPopularTracksUrl() called for permalink: " + artistPermalink + ", limit: " + limit);
		System.out.println("========================================");
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for popular-tracks URL request.");
				return new ArrayList<Map<String, Object>>();
			}
			
			// Construct the popular-tracks URL
			String popularTracksUrl = "https://soundcloud.com/" + artistPermalink + "/popular-tracks";
			String resolveUrl = soundCloudApiBaseUrl + "/resolve?url=" + java.net.URLEncoder.encode(popularTracksUrl, "UTF-8");
			System.out.println("Resolving popular-tracks URL: " + popularTracksUrl);
			
			HttpHeaders resolveHeaders = new HttpHeaders();
			resolveHeaders.setBearerAuth(accessToken);
			resolveHeaders.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			resolveHeaders.set("Accept", "application/json");
			HttpEntity<String> resolveRequest = new HttpEntity<String>(resolveHeaders);
			
			ResponseEntity<Map<String, Object>> resolveResponse;
			try {
				resolveResponse = restTemplate.exchange(
					resolveUrl,
					HttpMethod.GET,
					resolveRequest,
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
			} catch (org.springframework.web.client.HttpClientErrorException e) {
				System.err.println("HTTP error resolving popular-tracks URL: " + e.getStatusCode() + " - " + e.getMessage());
				System.err.println("Response body: " + e.getResponseBodyAsString());
				return new ArrayList<Map<String, Object>>();
			} catch (Exception e) {
				System.err.println("Exception resolving popular-tracks URL: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Resolve response status: " + resolveResponse.getStatusCode());
			
			if (resolveResponse.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> resolved = resolveResponse.getBody();
				if (resolved == null) {
					System.err.println("Resolved popular-tracks response body is null");
					return new ArrayList<Map<String, Object>>();
				}
				Object kind = resolved.get("kind");
				System.out.println("Resolved kind: " + kind);
				
				// If it resolves to a playlist, get tracks from the playlist
				if ("playlist".equals(kind) || "set".equals(kind)) {
					// First, check if tracks are directly in the resolved object
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> directTracks = (List<Map<String, Object>>) resolved.get("tracks");
					if (directTracks != null && !directTracks.isEmpty()) {
						System.out.println("Found " + directTracks.size() + " tracks directly in resolved playlist");
						return directTracks.stream().limit(limit).collect(java.util.stream.Collectors.toList());
					}
					
					// If not, try to get tracks from the playlist ID
					Object playlistId = resolved.get("id");
					System.out.println("Resolved playlist ID: " + playlistId);
					if (playlistId != null) {
						String tracksUrl = soundCloudApiBaseUrl + "/playlists/" + playlistId + "/tracks?limit=" + limit + "&linked_partitioning=true";
						System.out.println("Fetching tracks from playlist URL: " + tracksUrl);
						
						HttpHeaders headers = new HttpHeaders();
						headers.setBearerAuth(accessToken);
						headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
						headers.set("Accept", "application/json");
						HttpEntity<String> request = new HttpEntity<String>(headers);
						
						// Try as paginated response with collection field
						ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
							tracksUrl,
							HttpMethod.GET,
							request,
							new ParameterizedTypeReference<Map<String, Object>>(){}
						);
						
						System.out.println("Tracks API response status: " + response.getStatusCode());
						
						if (response.getStatusCode().is2xxSuccessful()) {
							Map<String, Object> responseBody = response.getBody();
							if (responseBody == null) {
								System.err.println("Tracks response body is null for playlist URL: " + tracksUrl);
								return new ArrayList<Map<String, Object>>();
							}
							System.out.println("Tracks response body keys: " + responseBody.keySet());
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
							
							if (tracks != null && !tracks.isEmpty()) {
								System.out.println("Found " + tracks.size() + " popular tracks from playlist");
								return tracks.stream().limit(limit).collect(java.util.stream.Collectors.toList());
							}
						}
					}
				} else {
					System.err.println("Resolved kind is not 'playlist' or 'set' for popular-tracks URL, kind: " + kind);
				}
			} else {
				System.err.println("Failed to resolve popular-tracks URL - status: " + resolveResponse.getStatusCode());
			}
			
			return new ArrayList<Map<String, Object>>();
			
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromPopularTracksUrl for permalink: " + artistPermalink);
			System.err.println("Error: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}
	
	/**
	 * Gets tracks from an artist using their user ID directly (more reliable than resolving permalink).
	 */
	private List<Map<String, Object>> getTracksFromArtistByUserId(String userId, int limit) {
		System.out.println("========================================");
		System.out.println("getTracksFromArtistByUserId() called for user ID: " + userId + ", limit: " + limit);
		System.out.println("========================================");
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for artist tracks request.");
				return new ArrayList<Map<String, Object>>();
			}
			
			// Fetch a large number of tracks to ensure we get the popular ones
			// SoundCloud returns tracks in chronological order, so we need to fetch many and sort by popularity
			int fetchLimit = Math.max(limit * 20, 200); // Fetch at least 200 tracks to get popular ones
			String tracksUrl = soundCloudApiBaseUrl + "/users/" + userId + "/tracks?limit=" + fetchLimit + "&linked_partitioning=true";
			System.out.println("Fetching tracks from URL: " + tracksUrl);
			System.out.println("Fetching " + fetchLimit + " tracks to find the most popular ones");
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			ResponseEntity<Map<String, Object>> response;
			try {
				response = restTemplate.exchange(
					tracksUrl,
					HttpMethod.GET,
					request,
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
				
				System.out.println("Tracks API response status: " + response.getStatusCode());
				
				if (response.getStatusCode().is2xxSuccessful()) {
					Map<String, Object> responseBody = response.getBody();
					if (responseBody == null) {
						System.err.println("Tracks response body is null for user ID: " + userId);
						return new ArrayList<Map<String, Object>>();
					}
					System.out.println("Response body keys: " + responseBody.keySet());
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
					
					if (tracks != null && !tracks.isEmpty()) {
						System.out.println("Found " + tracks.size() + " tracks, sorting by popularity (playback_count)...");
						// Sort by playback_count (descending) to get the most popular tracks
						// This ensures we get the artist's actual popular tracks, not just recent ones
						List<Map<String, Object>> sortedTracks = tracks.stream()
							.sorted((a, b) -> {
								long countA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
								long countB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
								return Long.compare(countB, countA); // Descending order (most popular first)
							})
							.limit(limit)
							.collect(java.util.stream.Collectors.toList());
						
						System.out.println("SUCCESS - Returning " + sortedTracks.size() + " popular tracks (sorted by playback_count) for user ID: " + userId);
						if (!sortedTracks.isEmpty()) {
							long topPlayCount = ((Number) sortedTracks.get(0).getOrDefault("playback_count", 0)).longValue();
							System.out.println("Top track playback_count: " + topPlayCount);
						}
						return sortedTracks;
					} else {
						System.err.println("No tracks found in collection for user ID: " + userId);
					}
				} else {
					System.err.println("Failed to fetch tracks - status: " + response.getStatusCode());
				}
			} catch (org.springframework.web.client.HttpClientErrorException e) {
				System.err.println("HTTP error fetching tracks: " + e.getStatusCode() + " - " + e.getMessage());
				System.err.println("Response body: " + e.getResponseBodyAsString());
			} catch (Exception e) {
				System.err.println("Exception fetching tracks: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
			}
			
			return new ArrayList<Map<String, Object>>();
			
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromArtistByUserId for user ID: " + userId);
			System.err.println("Error: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
			return new ArrayList<Map<String, Object>>();
		}
	}
	
	/**
	 * Fallback method to get tracks from artist by resolving their profile and fetching tracks.
	 */
	private List<Map<String, Object>> getTracksFromArtistFallback(String artistPermalink, int limit, String accessToken) {
		System.out.println("========================================");
		System.out.println("getTracksFromArtistFallback() called for: " + artistPermalink);
		System.out.println("========================================");
		try {
			if (accessToken == null) {
				System.err.println("Fallback: No access token available");
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Trying fallback method for artist: " + artistPermalink);
			
			// Resolve the artist's profile URL to get their user ID
			String artistUrl = "https://soundcloud.com/" + artistPermalink;
			String resolveUrl = soundCloudApiBaseUrl + "/resolve?url=" + java.net.URLEncoder.encode(artistUrl, "UTF-8");
			System.out.println("Fallback: Resolving artist profile URL: " + artistUrl);
			
			HttpHeaders resolveHeaders = new HttpHeaders();
			resolveHeaders.setBearerAuth(accessToken);
			resolveHeaders.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			resolveHeaders.set("Accept", "application/json");
			HttpEntity<String> resolveRequest = new HttpEntity<String>(resolveHeaders);
			
			ResponseEntity<Map<String, Object>> resolveResponse;
			try {
				resolveResponse = restTemplate.exchange(
					resolveUrl,
					HttpMethod.GET,
					resolveRequest,
					new ParameterizedTypeReference<Map<String, Object>>(){}
				);
			} catch (org.springframework.web.client.HttpClientErrorException e) {
				System.err.println("Fallback: HTTP error resolving artist profile: " + e.getStatusCode() + " - " + e.getMessage());
				System.err.println("Fallback: Response body: " + e.getResponseBodyAsString());
				return new ArrayList<Map<String, Object>>();
			} catch (Exception e) {
				System.err.println("Fallback: Exception resolving artist profile: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<Map<String, Object>>();
			}
			
			System.out.println("Fallback: Resolve response status: " + resolveResponse.getStatusCode());
			
			if (resolveResponse.getStatusCode().is2xxSuccessful()) {
				Map<String, Object> resolved = resolveResponse.getBody();
				if (resolved == null) {
					System.err.println("Fallback: Resolved artist profile response body is null");
					return new ArrayList<Map<String, Object>>();
				}
				Object kind = resolved.get("kind");
				System.out.println("Fallback: Resolved kind: " + kind);
				
				if ("user".equals(kind)) {
					Object userId = resolved.get("id");
					System.out.println("Fallback: Resolved user ID: " + userId);
					if (userId != null) {
						// Try multiple approaches to get tracks
						
						// Approach 1: Try /users/{userId}/tracks (may only work for authenticated user's own tracks)
						// Fetch a large number of tracks to ensure we get the popular ones
						// SoundCloud returns tracks in chronological order (newest first), so we need to fetch many and sort by popularity
						int fetchLimit = Math.max(limit * 20, 200); // Fetch at least 200 tracks to get popular ones
						String tracksUrl = soundCloudApiBaseUrl + "/users/" + userId + "/tracks?limit=" + fetchLimit + "&linked_partitioning=true";
						System.out.println("Fallback: Attempting Approach 1 - Fetching tracks from URL: " + tracksUrl);
						System.out.println("Fallback: Fetching " + fetchLimit + " tracks to find the most popular ones (not just recent)");
						
						HttpHeaders headers = new HttpHeaders();
						headers.setBearerAuth(accessToken);
						headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
						headers.set("Accept", "application/json");
						HttpEntity<String> request = new HttpEntity<String>(headers);
						
						ResponseEntity<Map<String, Object>> response;
						try {
							response = restTemplate.exchange(
								tracksUrl,
								HttpMethod.GET,
								request,
								new ParameterizedTypeReference<Map<String, Object>>(){}
							);
							
							System.out.println("Fallback: Approach 1 - Tracks API response status: " + response.getStatusCode());
							
							if (response.getStatusCode().is2xxSuccessful()) {
								Map<String, Object> responseBody = response.getBody();
								if (responseBody == null) {
									System.err.println("Fallback: Tracks response body is null for artist: " + artistPermalink);
									return new ArrayList<Map<String, Object>>();
								}
								System.out.println("Fallback: Approach 1 - Response body keys: " + responseBody.keySet());
								@SuppressWarnings("unchecked")
								List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
								
								if (tracks != null && !tracks.isEmpty()) {
									System.out.println("Fallback: Approach 1 - Found " + tracks.size() + " tracks, sorting by popularity (playback_count)...");
									// Sort by playback_count (descending) to get the most popular tracks
									// This ensures we get the artist's actual popular tracks, not just recent ones
									List<Map<String, Object>> sortedTracks = tracks.stream()
										.sorted((a, b) -> {
											long countA = ((Number) a.getOrDefault("playback_count", 0)).longValue();
											long countB = ((Number) b.getOrDefault("playback_count", 0)).longValue();
											return Long.compare(countB, countA); // Descending order (most popular first)
										})
										.limit(limit)
										.collect(java.util.stream.Collectors.toList());
									
									System.out.println("Fallback: Approach 1 - SUCCESS - Returning " + sortedTracks.size() + " popular tracks (sorted by playback_count) for artist: " + artistPermalink);
									if (!sortedTracks.isEmpty()) {
										long topPlayCount = ((Number) sortedTracks.get(0).getOrDefault("playback_count", 0)).longValue();
										System.out.println("Fallback: Top track playback_count: " + topPlayCount);
									}
									return sortedTracks;
								}
							}
						} catch (org.springframework.web.client.HttpClientErrorException e) {
							System.err.println("Fallback: Approach 1 - HTTP error fetching tracks: " + e.getStatusCode() + " - " + e.getMessage());
							System.err.println("Fallback: Approach 1 - Response body: " + e.getResponseBodyAsString());
							// Continue to Approach 2
						} catch (Exception e) {
							System.err.println("Fallback: Approach 1 - Exception fetching tracks: " + e.getClass().getSimpleName() + " - " + e.getMessage());
							// Continue to Approach 2
						}
						
						// Approach 2: Use search API to find tracks by the artist's username
						System.out.println("Fallback: Attempting Approach 2 - Using search API for artist: " + artistPermalink);
						try {
							String searchUrl = soundCloudApiBaseUrl + "/tracks?q=" + java.net.URLEncoder.encode(artistPermalink, "UTF-8") + "&limit=" + (limit * 5) + "&linked_partitioning=true";
							System.out.println("Fallback: Approach 2 - Search URL: " + searchUrl);
							
							ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
								searchUrl,
								HttpMethod.GET,
								request,
								new ParameterizedTypeReference<Map<String, Object>>(){}
							);
							
							System.out.println("Fallback: Approach 2 - Search response status: " + searchResponse.getStatusCode());
							
							if (searchResponse.getStatusCode().is2xxSuccessful()) {
								Map<String, Object> searchBody = searchResponse.getBody();
								if (searchBody == null) {
									System.err.println("Fallback: Search response body is null for artist: " + artistPermalink);
									return new ArrayList<Map<String, Object>>();
								}
								System.out.println("Fallback: Approach 2 - Search response body keys: " + searchBody.keySet());
								@SuppressWarnings("unchecked")
								List<Map<String, Object>> searchTracks = (List<Map<String, Object>>) searchBody.get("collection");
								
								if (searchTracks != null && !searchTracks.isEmpty()) {
									// Filter tracks to only include those by this artist
									List<Map<String, Object>> artistTracks = new ArrayList<Map<String, Object>>();
									for (Map<String, Object> track : searchTracks) {
										Object userObj = track.get("user");
										if (userObj instanceof Map) {
											@SuppressWarnings("unchecked")
											Map<String, Object> trackUser = (Map<String, Object>) userObj;
											String trackUserId = String.valueOf(trackUser.get("id"));
											if (trackUserId.equals(String.valueOf(userId))) {
												artistTracks.add(track);
											}
										}
									}
									
									if (!artistTracks.isEmpty()) {
										System.out.println("Fallback: Approach 2 - Found " + artistTracks.size() + " tracks by artist, sorting by popularity...");
										// Sort by playback_count (descending) and limit
										List<Map<String, Object>> sortedTracks = artistTracks.stream()
											.sorted((a, b) -> Long.compare(
												((Number) b.getOrDefault("playback_count", 0)).longValue(),
												((Number) a.getOrDefault("playback_count", 0)).longValue()))
											.limit(limit)
											.collect(java.util.stream.Collectors.toList());
										
										System.out.println("Fallback: Approach 2 - SUCCESS - Returning " + sortedTracks.size() + " tracks for artist: " + artistPermalink);
										return sortedTracks;
									}
								}
							}
						} catch (Exception e) {
							System.err.println("Fallback: Approach 2 - Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
							e.printStackTrace();
						}
						
						System.err.println("Fallback: Both approaches failed - No tracks found for artist: " + artistPermalink);
					} else {
						System.err.println("Fallback: User ID is null for artist: " + artistPermalink);
					}
				} else {
					System.err.println("Fallback: Resolved kind is not 'user' for artist: " + artistPermalink + ", kind: " + kind);
				}
			} else {
				System.err.println("Fallback: Failed to resolve artist profile - status: " + resolveResponse.getStatusCode());
			}
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromArtistFallback for: " + artistPermalink);
			System.err.println("Error: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
		}
		System.err.println("Fallback: Returning empty list for artist: " + artistPermalink);
		return new ArrayList<Map<String, Object>>();
	}

	/**
	 * Gets the most common genre from trending/popular tracks (legacy method, kept for compatibility).
	 * Analyzes genres from trending tracks and returns the most frequently occurring genre.
	 * 
	 * @return The most common genre name, or empty string if none found
	 */
	public String getFeaturedGenre() {
		try {
			List<Map<String, Object>> popularTracks = getPopularTracks(30);
			System.out.println("getFeaturedGenre: Retrieved " + popularTracks.size() + " popular tracks");
			if (popularTracks.isEmpty()) {
				System.err.println("getFeaturedGenre: No popular tracks available");
				return "";
			}
			
			// Count genres from trending tracks
			Map<String, Integer> genreCounts = new HashMap<String, Integer>();
			Map<String, Double> genrePopularityScores = new HashMap<String, Double>();
			
			for (Map<String, Object> track : popularTracks) {
				// Extract genre from track
				Object genreObj = track.get("genre");
				if (genreObj instanceof String && !((String) genreObj).isBlank()) {
					String genre = ((String) genreObj).trim();
					if (!genre.isEmpty()) {
						genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
						
						// Also consider popularity score for genre ranking
						double popularityScore = ((Number) track.getOrDefault("_popularity_score",
							((Number) track.getOrDefault("playback_count", 0)).doubleValue())).doubleValue();
						genrePopularityScores.put(genre, genrePopularityScores.getOrDefault(genre, 0.0) + popularityScore);
					}
				}
			}
			
			if (genreCounts.isEmpty()) {
				return "";
			}
			
			// Find genre with highest combined score (count + popularity score)
			return genreCounts.entrySet().stream()
				.max((a, b) -> {
					// Compare by count first, then by popularity score
					int countCompare = Integer.compare(a.getValue(), b.getValue());
					if (countCompare != 0) {
						return countCompare;
					}
					// If counts are equal, compare by popularity score
					double scoreA = genrePopularityScores.getOrDefault(a.getKey(), 0.0);
					double scoreB = genrePopularityScores.getOrDefault(b.getKey(), 0.0);
					return Double.compare(scoreB, scoreA);
				})
				.map(Map.Entry::getKey)
				.orElse("");
		} catch (Exception e) {
			System.err.println("Error fetching featured genre: " + e.getMessage());
			e.printStackTrace();
			return "";
		}
	}

	/**
	 * Aggregates and calculates a user's full SoundCloud "Wrapped" statistics.
     * Returns raw summary data including profile info, top tracks, artists,
     * playlists, listening hours, reposts, and fun facts.
     *
     * This is a data-heavy method meant to be consumed internally
     * or reformatted by higher-level methods.
	 * 
	 * @return             Map containing unformatted Wrapped statistics
	 */
	public Map<String, Object> getFullWrappedSummary() {
		Map<String, Object> wrapped = new HashMap<String, Object>();
		
		// Get profile first (this is the most important and usually works)
		Map<String, Object> profile = new HashMap<String, Object>();
		try {
			profile = getUserProfile();
		} catch (Exception e) {
			System.out.println("Failed to fetch profile in wrapped summary: " + e.getMessage());
			// Use empty profile with defaults
			profile.put("username", "Unknown");
			profile.put("followers_count", 0);
			profile.put("followings_count", 0);
			profile.put("track_count", 0);
			profile.put("playlist_count", 0);
			profile.put("public_favorites_count", 0);
			profile.put("reposts_count", 0);
			profile.put("comments_count", 0);
			profile.put("upload_seconds_left", 0);
			profile.put("created_at", "2024/01/01 00:00:00 +0000");
		}
		
		// Add delays between API calls to avoid rate limiting (SoundCloud has strict rate limits)
		List<Map<String, Object>> likes = new ArrayList<Map<String, Object>>();
		try {
			Thread.sleep(500); // 500ms delay
			likes = getUserLikes();
		} catch (Exception e) {
			System.out.println("Failed to fetch likes: " + e.getMessage());
		}
		
		List<Map<String, Object>> tracks = new ArrayList<Map<String, Object>>();
		try {
			Thread.sleep(500); // 500ms delay
			tracks = getUserTracks();
		} catch (Exception e) {
			System.out.println("Failed to fetch tracks: " + e.getMessage());
		}
		
		List<Map<String, Object>> playlists = new ArrayList<Map<String, Object>>();
		try {
			Thread.sleep(500); // 500ms delay
			playlists = getUserPlaylists();
		} catch (Exception e) {
			System.out.println("Failed to fetch playlists: " + e.getMessage());
		}
		
		List<Map<String, Object>> followers = new ArrayList<Map<String, Object>>();
		try {
			Thread.sleep(500); // 500ms delay
			followers = getUserFollowers();
		} catch (Exception e) {
			System.out.println("Failed to fetch followers: " + e.getMessage());
		}

		//Profile-level statistics
		wrapped.put("username", profile.get("username"));
		wrapped.put("fullName", profile.get("full_name"));
		wrapped.put("followers", profile.get("followers_count"));
		wrapped.put("following", profile.get("followings_count"));
		wrapped.put("reposts", profile.get("reposts_count"));
		wrapped.put("tracksUploaded", profile.get("track_count"));
		wrapped.put("playlistsCreated", profile.get("playlist_count"));
		wrapped.put("commentsPosted", profile.get("comments_count"));
		wrapped.put("remainingUploadQuotaSeconds", profile.get("upload_seconds_left"));

		String createdAt = (String) profile.get("created_at");

		if (createdAt != null) {
			wrapped.put("accountAgeYears", calculateAccountAgeYears(createdAt));
		}

		// Calculate stats for the past year using tracked activity
		String soundcloudUserId = String.valueOf(profile.getOrDefault("id", ""));
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		java.time.LocalDateTime oneYearAgo = now.minusYears(1);
		
		// Get tracked listening time for the past year (in milliseconds)
		long totalListeningTimeMs = activityTrackingService.getTotalListeningTimeMs(
			soundcloudUserId, oneYearAgo, now);
		double totalListeningHours = totalListeningTimeMs / 1000.0 / 60.0 / 60.0;
		wrapped.put("totalListeningHours", totalListeningHours);
		
		// Get tracked likes for the past year
		long likesGiven = activityTrackingService.getTotalLikes(soundcloudUserId, oneYearAgo, now);
		wrapped.put("likesGiven", likesGiven);

		// Calculate books based on actual listening time
		// Assuming reading speed: 1 hour of listening = 1 hour of reading
		// Average book length: 300 pages, reading speed: 50 pages/hour
		// So: 1 hour listening = 50/300 = 1/6 of a book
		int estimatedBooksRead = (int) (totalListeningHours / 6.0);
		wrapped.put("booksYouCouldHaveRead", estimatedBooksRead);

		int followerCount = (int) profile.getOrDefault("followers_count", 0);
		wrapped.put("funFact", followerCount > 1000 ? "You're pretty famous! 🎉" : "Every star starts small 🥹");

		int followingCount = (int) profile.getOrDefault("following", 0);
		double followRatio = followingCount == 0 ? followerCount : ((double) followerCount / followingCount);

		if (followingCount == 0 && followerCount > 0) {
			wrapped.put("followRatioFact", "You have followers but aren’t following anyone — true influencer vibes! 😎");
		}

		else if (followRatio > 1.0) {
			wrapped.put("followRatioFact", String.format("You have %.1f times more followers than people you follow!", followRatio));
		}

		if (!followers.isEmpty()) {
			followers.sort(Comparator.comparing(f -> (String) f.get("created_at")));
			Map<String, Object> newestFollower = followers.get(followers.size() - 1);
			String followerName = (String) newestFollower.getOrDefault("username", "");
			wrapped.put("newestFollower", String.format("Your newest follower this year is @%s!", followerName));
		}

		//Top 5 tracks by play count (deduplicated by track ID)
		// Use a LinkedHashMap to preserve order while deduplicating by track ID
		Map<String, Map<String, Object>> uniqueTracks = new LinkedHashMap<String, Map<String, Object>>();
		for (Map<String, Object> track : tracks) {
			Object trackId = track.get("id");
			if (trackId != null) {
				String trackIdStr = String.valueOf(trackId);
				// Only add if we haven't seen this track ID before, or if this one has a higher playback_count
				if (!uniqueTracks.containsKey(trackIdStr)) {
					uniqueTracks.put(trackIdStr, track);
				} else {
					// If duplicate, keep the one with higher playback_count
					Map<String, Object> existing = uniqueTracks.get(trackIdStr);
					long existingCount = ((Number) existing.getOrDefault("playback_count", 0)).longValue();
					long newCount = ((Number) track.getOrDefault("playback_count", 0)).longValue();
					if (newCount > existingCount) {
						uniqueTracks.put(trackIdStr, track);
					}
				}
			}
		}
		
		List<Map<String, Object>> topTracks = uniqueTracks.values().stream()
				.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("playback_count", 0)).longValue(),
						((Number) a.getOrDefault("playback_count", 0)).longValue()))
				.limit(5)
				.collect(java.util.stream.Collectors.toList());
		wrapped.put("topTracks", topTracks);

		//Top 5 liked playlists
		List<Map<String, Object>> topLikedPlaylists = playlists.stream()
				.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("likes_count", 0)).longValue(),
						((Number) a.getOrDefault("likes_count", 0)).longValue()))
				.limit(5).toList();
		wrapped.put("topLikedPlaylists", topLikedPlaylists);

		Map<String, Integer> artistCounts = countTopArtists(likes);
		List<String> topLikedArtists = topNKeys(artistCounts, 5); //Top 5 liked artists
		wrapped.put("topLikedArtists", topLikedArtists);

		Map<String, Long> artistListeningMs = calculateArtistListeningMs(tracks);
		Map<String, Double> artistListeningHours = convertMsToHours(artistListeningMs);
		wrapped.put("artistListeningHours", artistListeningHours);

		//Top 5 artists by listening hours
		List<String> topArtistsByHours = topNKeys(artistListeningHours, 5);
		wrapped.put("topArtistsByHours", topArtistsByHours);

		// Genre analysis
		Map<String, Object> genreAnalysis = genreAnalysisService.analyzeGenres(tracks);
		wrapped.put("genreAnalysis", genreAnalysis);
		wrapped.put("topGenres", genreAnalysisService.getTop5Genres(tracks));
		wrapped.put("genreDiscoveryCount", genreAnalysis.get("totalGenresDiscovered"));

		try {
			Map<Integer, Integer> yearCounts = countYears(likes);
			String peakYear = yearCounts.entrySet().stream()
					.max(Map.Entry.comparingByValue())
					.map(entry -> entry.getKey() + " (" + entry.getValue() + " likes)")
					.orElse("No data");
			wrapped.put("peakYear", peakYear);
		}

		catch (ParseException e) {
			wrapped.put("peakYear", "No data");
		}

		List<Map<String, Object>> topRepostedTracks = topRepostedTracks(tracks, 5);
		wrapped.put("topRepostedTracks", topRepostedTracks);

		//Global music taste placeholder
		wrapped.put("globalTasteComparison", "Listeners in New York, Berlin and Tokyo share your musical taste! 🎧");

		// Calculate "Support the Underground" metric
		// Percentage of listening time to artists with <5,000 followers
		double undergroundSupportPercentage = calculateUndergroundSupportPercentage(likes, tracks);
		wrapped.put("undergroundSupportPercentage", undergroundSupportPercentage);

		// Calculate "The Trendsetter" (Early Adopter) Score
		Map<String, Object> trendsetterData = calculateTrendsetterScore(soundcloudUserId, tracks);
		wrapped.put("trendsetterScore", trendsetterData);

		// Calculate "The Repost King/Queen" metric
		Map<String, Object> repostKingData = calculateRepostKingScore(soundcloudUserId, likes);
		wrapped.put("repostKingScore", repostKingData);

		// Generate "The Sonic Archetype" (Musical Persona)
		String sonicArchetype = generateSonicArchetype(tracks, likes, topArtistsByHours, genreAnalysis);
		wrapped.put("sonicArchetype", sonicArchetype);

		// Generate "Music Age" (Old Soul / Young at Heart)
		@SuppressWarnings("unchecked")
		List<String> topGenresList = (List<String>) genreAnalysis.getOrDefault("topGenres", List.of());
		String musicAge = generateMusicAge(tracks, likes, topGenresList);
		wrapped.put("musicAge", musicAge);

		return wrapped;
    }

	/**
	 * Calculates the percentage of listening time dedicated to "underground" artists
	 * (artists with fewer than 5,000 followers).
	 * 
	 * @param likes List of liked tracks (to get artist follower counts)
	 * @param tracks List of user's tracks (to calculate listening time)
	 * @return Percentage (0-100) of listening time to underground artists
	 */
	private double calculateUndergroundSupportPercentage(List<Map<String, Object>> likes, List<Map<String, Object>> tracks) {
		if (likes == null || likes.isEmpty() || tracks == null || tracks.isEmpty()) {
			return 0.0;
		}

		// Create a map of artist username to follower count from likes
		Map<String, Integer> artistFollowers = new HashMap<String, Integer>();
		for (Map<String, Object> like : likes) {
			Object userObj = like.get("user");
			if (userObj instanceof Map<?, ?> userMap) {
				Object usernameObj = userMap.get("username");
				Object followersObj = userMap.get("followers_count");
				
				if (usernameObj instanceof String username && followersObj instanceof Number) {
					int followers = ((Number) followersObj).intValue();
					// Only store if not already present (first occurrence wins)
					artistFollowers.putIfAbsent(username, followers);
				}
			}
		}

		// Calculate total listening time and underground listening time
		long totalListeningMs = 0;
		long undergroundListeningMs = 0;
		int undergroundThreshold = 5000; // Artists with <5,000 followers are "underground"

		for (Map<String, Object> track : tracks) {
			long duration = ((Number) track.getOrDefault("duration", 0)).longValue();
			totalListeningMs += duration;

			Object userObj = track.get("user");
			if (userObj instanceof Map<?, ?> userMap) {
				Object usernameObj = userMap.get("username");
				if (usernameObj instanceof String username) {
					Integer followers = artistFollowers.get(username);
					// If we don't have follower data, check directly from track's user object
					if (followers == null) {
						Object followersObj = userMap.get("followers_count");
						if (followersObj instanceof Number) {
							followers = ((Number) followersObj).intValue();
						}
					}
					
					// Count as underground if followers < threshold or unknown (assume small)
					if (followers == null || followers < undergroundThreshold) {
						undergroundListeningMs += duration;
					}
				}
			}
		}

		if (totalListeningMs == 0) {
			return 0.0;
		}

		return (double) undergroundListeningMs / totalListeningMs * 100.0;
	}

	/**
	 * Generates a "Year in Review" poem using Groq AI based on the user's top tracks and genres.
	 * 
	 * @param topTracks List of top tracks with title and artist
	 * @param topGenres List of top genre names
	 * @param username User's username
	 * @return A creative poem summarizing the user's musical year
	 */
	private String generateYearInReviewPoetry(List<Map<String, Object>> topTracks, List<String> topGenres, String username) {
		if (topTracks == null || topTracks.isEmpty()) {
			return "Your musical journey this year was quiet, but every silence holds a song waiting to be discovered.";
		}

		try {
			// Build context from top tracks and genres
			StringBuilder context = new StringBuilder();
			context.append("User: ").append(username).append("\n\n");
			
			context.append("Top Tracks:\n");
			for (int i = 0; i < Math.min(5, topTracks.size()); i++) {
				Map<String, Object> track = topTracks.get(i);
				String title = (String) track.getOrDefault("title", "Unknown Track");
				String artist = (String) track.getOrDefault("artist", "Unknown Artist");
				context.append((i + 1)).append(". \"").append(title).append("\" by ").append(artist).append("\n");
			}
			
			if (topGenres != null && !topGenres.isEmpty()) {
				context.append("\nTop Genres: ").append(String.join(", ", topGenres.subList(0, Math.min(5, topGenres.size()))));
			}

			// Create prompt for poetry generation
			String prompt = String.format(
				"Write a short, creative poem (4-6 lines) celebrating a music listener's year based on their listening data. " +
				"The poem should be inspiring, personal, and capture the essence of their musical journey. " +
				"Use the track titles and genres naturally in the poem. Make it feel like a personalized 'Year in Review' card. " +
				"Keep it concise and poetic.\n\n" +
				"User Data:\n%s",
				context.toString()
			);

			// Use Groq to generate the poem
			String poetry = getGroqDescription("Year in Review", "poetry", prompt);
			
			if (poetry != null && !poetry.trim().isEmpty()) {
				return poetry.trim();
			}
		} catch (Exception e) {
			System.out.println("Error generating year in review poetry: " + e.getMessage());
		}

		// Fallback poem
		return String.format(
			"In the rhythm of %s, you found your sound,\n" +
			"Each track a memory, each beat a moment found.\n" +
			"Your musical year, a symphony of discovery,\n" +
			"Here's to the songs that became your story.",
			username
		);
	}

	/**
	 * Overloaded getGroqDescription that accepts a custom prompt.
	 */
	private String getGroqDescription(String entityName, String entityType, String customPrompt) {
		if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
			String systemPropKey = System.getProperty("GROQ_API_KEY");
			if (systemPropKey != null && !systemPropKey.trim().isEmpty()) {
				groqApiKey = systemPropKey;
			} else {
				String envKey = System.getenv("GROQ_API_KEY");
				if (envKey != null && !envKey.trim().isEmpty()) {
					groqApiKey = envKey;
				}
			}
		}

		if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
			return null;
		}

		try {
			String url = groqBaseUrl + "/chat/completions";
			
			Map<String, Object> requestBody = new HashMap<String, Object>();
			List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
			Map<String, Object> userMessage = new HashMap<String, Object>();
			userMessage.put("role", "user");
			userMessage.put("content", customPrompt);
			messages.add(userMessage);
			
			requestBody.put("model", "llama-3.3-70b-versatile");
			requestBody.put("messages", messages);
			requestBody.put("temperature", 0.8); // Higher temperature for more creative output
			requestBody.put("max_tokens", 200);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(groqApiKey);
			
			HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(requestBody, headers);
			
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				request,
				new ParameterizedTypeReference<Map<String, Object>>() {}
			);

			Map<String, Object> responseBody = response.getBody();
			if (responseBody != null) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
				if (choices != null && !choices.isEmpty()) {
					Map<String, Object> firstChoice = choices.get(0);
					@SuppressWarnings("unchecked")
					Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
					if (message != null) {
						String content = (String) message.get("content");
						return content != null ? content.trim() : null;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Error calling Groq API for poetry: " + e.getMessage());
		}

		return null;
	}

	/**
	 * Calculates "The Trendsetter" (Early Adopter) Score.
	 * Measures how early a user discovered tracks compared to when they were created,
	 * weighted by the track's current popularity.
	 * 
	 * @param userId SoundCloud user ID
	 * @param tracks List of tracks the user has played
	 * @return Map containing score, badge, and description
	 */
	private Map<String, Object> calculateTrendsetterScore(String userId, List<Map<String, Object>> tracks) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		if (userId == null || userId.isEmpty() || tracks == null || tracks.isEmpty()) {
			result.put("score", 0);
			result.put("badge", "Listener");
			result.put("description", "Keep exploring to discover your trendsetter potential!");
			result.put("visionaryTracks", 0);
			return result;
		}

		try {
			// Get first play timestamps for each track from UserActivity
			java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusYears(1);
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			
			List<com.soundwrapped.entity.UserActivity> playActivities = 
				userActivityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
					userId, 
					com.soundwrapped.entity.UserActivity.ActivityType.PLAY,
					oneYearAgo,
					now
				);

			// Create a map of trackId -> first play timestamp (earliest play)
			Map<String, java.time.LocalDateTime> firstPlayTimestamps = new HashMap<String, java.time.LocalDateTime>();
			for (com.soundwrapped.entity.UserActivity activity : playActivities) {
				String trackId = activity.getTrackId();
				java.time.LocalDateTime playTime = activity.getCreatedAt();
				// Get existing timestamp or use current play time
				java.time.LocalDateTime existingTime = firstPlayTimestamps.get(trackId);
				if (existingTime == null || playTime.isBefore(existingTime)) {
					firstPlayTimestamps.put(trackId, playTime);
				}
			}

			// Calculate trendsetter score
			int visionaryTracks = 0; // Tracks played when they had <1,000 plays, now have >100,000
			int earlyAdopterTracks = 0; // Tracks played within 7 days of creation
			double totalScore = 0.0;

			// Create date formatter once (thread-safe alternative to SimpleDateFormat)
			java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z");

			for (Map<String, Object> track : tracks) {
				String trackId = String.valueOf(track.getOrDefault("id", ""));
				java.time.LocalDateTime firstPlay = firstPlayTimestamps.get(trackId);
				
				if (firstPlay == null) {
					continue; // Skip tracks without play history
				}

				// Get track creation date
				String createdAtStr = (String) track.getOrDefault("created_at", "");
				if (createdAtStr.isEmpty()) {
					continue;
				}

				try {
					// Parse creation date (format: "2024/01/15 12:00:00 +0000")
					// Use DateTimeFormatter (thread-safe) instead of SimpleDateFormat
					java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(createdAtStr, dateFormatter);
					java.time.LocalDateTime trackCreated = zonedDateTime.toLocalDateTime();

					// Calculate days between track creation and first play
					long daysBetween = java.time.Duration.between(trackCreated, firstPlay).toDays();
					
					// Get current playback count
					long currentPlays = ((Number) track.getOrDefault("playback_count", 0)).longValue();

					// Early adopter: played within 7 days of creation
					if (daysBetween >= 0 && daysBetween <= 7) {
						earlyAdopterTracks++;
						// Weight by current popularity
						totalScore += Math.min(currentPlays / 1000.0, 100.0); // Cap at 100 points per track
					}

					// Visionary: played when track likely had <1,000 plays, now has >100,000
					// Estimate: if played within 30 days of creation and now has >100k plays
					if (daysBetween >= 0 && daysBetween <= 30 && currentPlays > 100000) {
						visionaryTracks++;
						totalScore += 200.0; // Bonus points for visionary tracks
					}
				} catch (Exception e) {
					// Skip tracks with unparseable dates
					continue;
				}
			}

			// Determine badge and description
			String badge;
			String description;
			
			if (visionaryTracks >= 5 || totalScore >= 1000) {
				badge = "Visionary";
				description = String.format("You discovered %d tracks before they blew up! You're a true music visionary.", visionaryTracks);
			} else if (visionaryTracks >= 2 || totalScore >= 500) {
				badge = "Trendsetter";
				description = String.format("You have an ear for what's next! %d of your early discoveries became hits.", visionaryTracks);
			} else if (earlyAdopterTracks >= 5 || totalScore >= 200) {
				badge = "Early Adopter";
				description = String.format("You're always ahead of the curve! You discovered %d tracks within a week of release.", earlyAdopterTracks);
			} else if (earlyAdopterTracks >= 1 || totalScore >= 50) {
				badge = "Explorer";
				description = "You love discovering new music! Keep exploring to unlock your trendsetter potential.";
			} else {
				badge = "Listener";
				description = "Every music journey starts with listening. Keep exploring to discover your trendsetter side!";
			}

			result.put("score", (int) totalScore);
			result.put("badge", badge);
			result.put("description", description);
			result.put("visionaryTracks", visionaryTracks);
			result.put("earlyAdopterTracks", earlyAdopterTracks);
		} catch (Exception e) {
			System.out.println("Error calculating trendsetter score: " + e.getMessage());
			result.put("score", 0);
			result.put("badge", "Listener");
			result.put("description", "Keep exploring to discover your trendsetter potential!");
			result.put("visionaryTracks", 0);
			result.put("earlyAdopterTracks", 0);
		}

		return result;
	}

	/**
	 * Calculates "The Repost King/Queen" metric.
	 * Tracks how many reposted tracks went on to become popular/trending.
	 * 
	 * @param userId SoundCloud user ID
	 * @param likes List of liked tracks (to check repost status and popularity)
	 * @return Map containing count, percentage, and description
	 */
	private Map<String, Object> calculateRepostKingScore(String userId, List<Map<String, Object>> likes) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		if (userId == null || userId.isEmpty() || likes == null || likes.isEmpty()) {
			result.put("repostedTracks", 0);
			result.put("trendingTracks", 0);
			result.put("percentage", 0.0);
			result.put("badge", "Listener");
			result.put("description", "Start reposting to become a Repost King/Queen!");
			return result;
		}

		try {
			// Get all reposted tracks from UserActivity
			java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusYears(1);
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			
			List<com.soundwrapped.entity.UserActivity> repostActivities = 
				userActivityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
					userId, 
					com.soundwrapped.entity.UserActivity.ActivityType.REPOST,
					oneYearAgo,
					now
				);

			// Create a set of reposted track IDs
			Set<String> repostedTrackIds = new HashSet<String>();
			for (com.soundwrapped.entity.UserActivity activity : repostActivities) {
				repostedTrackIds.add(activity.getTrackId());
			}

			if (repostedTrackIds.isEmpty()) {
				result.put("repostedTracks", 0);
				result.put("trendingTracks", 0);
				result.put("percentage", 0.0);
				result.put("badge", "Listener");
				result.put("description", "Start reposting tracks you love to become a Repost King/Queen!");
				return result;
			}

			// Count how many reposted tracks became "trending" (high reposts_count)
			int totalReposted = repostedTrackIds.size();
			int trendingTracks = 0;

			// Optimize: Create a map of trackId -> reposts_count for O(1) lookup
			// Only process tracks that were actually reposted
			Map<String, Long> repostedTrackReposts = new HashMap<String, Long>();
			for (Map<String, Object> track : likes) {
				String trackId = String.valueOf(track.getOrDefault("id", ""));
				if (repostedTrackIds.contains(trackId)) {
					long repostsCount = ((Number) track.getOrDefault("reposts_count", 0)).longValue();
					repostedTrackReposts.put(trackId, repostsCount);
					
					// Consider "trending" if reposts_count > 1000
					if (repostsCount > 1000) {
						trendingTracks++;
					}
				}
			}

			double percentage = totalReposted > 0 ? (double) trendingTracks / totalReposted * 100.0 : 0.0;

			// Determine badge and description
			String badge;
			String description;
			
			if (trendingTracks >= 10 || percentage >= 50.0) {
				badge = "Repost Royalty";
				description = String.format("You reposted %d tracks that went viral! You're the ultimate Repost Royalty.", trendingTracks);
			} else if (trendingTracks >= 5 || percentage >= 30.0) {
				badge = "Repost King/Queen";
				description = String.format("You have impeccable taste! %d of your reposts became trending hits.", trendingTracks);
			} else if (trendingTracks >= 2 || percentage >= 15.0) {
				badge = "Repost Enthusiast";
				description = String.format("You're building your reputation! %d of your reposts caught fire.", trendingTracks);
			} else if (totalReposted >= 5) {
				badge = "Repost Supporter";
				description = String.format("You reposted %d tracks this year! Keep sharing the music you love.", totalReposted);
			} else {
				badge = "Listener";
				description = "Start reposting tracks you love to become a Repost King/Queen!";
			}

			result.put("repostedTracks", totalReposted);
			result.put("trendingTracks", trendingTracks);
			result.put("percentage", percentage);
			result.put("badge", badge);
			result.put("description", description);
		} catch (Exception e) {
			System.out.println("Error calculating repost king score: " + e.getMessage());
			result.put("repostedTracks", 0);
			result.put("trendingTracks", 0);
			result.put("percentage", 0.0);
			result.put("badge", "Listener");
			result.put("description", "Start reposting to become a Repost King/Queen!");
		}

		return result;
	}

	/**
	 * Generates "The Sonic Archetype" - an AI-generated musical persona based on listening data.
	 * 
	 * @param tracks List of user's tracks
	 * @param likes List of liked tracks
	 * @param topArtists List of top artist names
	 * @param genreAnalysis Genre analysis data
	 * @return A creative persona description
	 */
	private String generateSonicArchetype(List<Map<String, Object>> tracks, List<Map<String, Object>> likes, 
		List<String> topArtists, Map<String, Object> genreAnalysis) {
		
		try {
			// Build context from user's music data
			StringBuilder context = new StringBuilder();
			context.append("Musical Profile:\n\n");
			
			// Top genres
			@SuppressWarnings("unchecked")
			List<String> topGenres = (List<String>) genreAnalysis.getOrDefault("topGenres", List.of());
			if (!topGenres.isEmpty()) {
				context.append("Top Genres: ").append(String.join(", ", topGenres.subList(0, Math.min(5, topGenres.size())))).append("\n");
			}
			
			// Top artists
			if (topArtists != null && !topArtists.isEmpty()) {
				context.append("Top Artists: ").append(String.join(", ", topArtists.subList(0, Math.min(5, topArtists.size())))).append("\n");
			}
			
			// Listening patterns
			long totalPlays = tracks.stream()
				.mapToLong(t -> ((Number) t.getOrDefault("playback_count", 0)).longValue())
				.sum();
			context.append("Total Plays: ").append(totalPlays).append("\n");
			
			// Create prompt for persona generation
			String prompt = String.format(
				"Based on this music listener's profile, create a creative and fun musical persona (archetype) for them. " +
				"Give them a catchy title like 'The 3 AM Lo-Fi Scholar' or 'The High-Octane Bass Hunter' and write 2-3 sentences " +
				"describing their musical personality, listening style, and what makes them unique. " +
				"Make it inspiring, personal, and capture the essence of their musical journey. " +
				"Be creative and use the genres and artists naturally in the description.\n\n" +
				"%s",
				context.toString()
			);

			// Use Groq to generate the persona
			String archetype = getGroqDescription("Sonic Archetype", "persona", prompt);
			
			if (archetype != null && !archetype.trim().isEmpty()) {
				return archetype.trim();
			}
		} catch (Exception e) {
			System.out.println("Error generating sonic archetype: " + e.getMessage());
		}

		// Fallback persona
		return "The Musical Explorer - You're on a journey through sound, discovering new artists and genres with an open heart and curious ears.";
	}

	/**
	 * Generates a "Music Age" persona (e.g., "Old Soul", "Young at Heart") based on the user's music taste.
	 * Analyzes genres, track creation dates, and artist eras to determine if the user has classic or modern taste.
	 * 
	 * @param tracks List of user's tracks
	 * @param likes List of user's liked tracks
	 * @param topGenres List of top genre names
	 * @return A creative persona describing the user's "music age" (e.g., "Old Soul", "Young at Heart", "Timeless")
	 */
	private String generateMusicAge(List<Map<String, Object>> tracks, List<Map<String, Object>> likes, List<String> topGenres) {
		try {
			// Build context from user's music data
			StringBuilder context = new StringBuilder();
			
			// Analyze genres for classic vs modern indicators
			// Use HashSet for O(1) lookup instead of O(n) stream operations
			Set<String> classicGenres = new HashSet<String>(Arrays.asList("jazz", "blues", "classical", "soul", "funk", "disco", "rock", "country", "folk", "gospel"));
			Set<String> modernGenres = new HashSet<String>(Arrays.asList("edm", "electronic", "dubstep", "trap", "hip hop", "rap", "pop", "indie", "alternative", "r&b"));
			
			int classicGenreCount = 0;
			int modernGenreCount = 0;
			
			if (topGenres != null && !topGenres.isEmpty()) {
				for (String genre : topGenres) {
					String genreLower = genre.toLowerCase();
					// Check if genre contains any classic genre keyword
					boolean isClassic = classicGenres.stream().anyMatch(g -> genreLower.contains(g));
					boolean isModern = modernGenres.stream().anyMatch(g -> genreLower.contains(g));
					
					if (isClassic) {
						classicGenreCount++;
					}
					if (isModern) {
						modernGenreCount++;
					}
				}
			}
			
			// Analyze track creation dates (if available)
			int oldTracks = 0; // Tracks older than 10 years
			int newTracks = 0; // Tracks newer than 2 years
			int totalTracksAnalyzed = 0;
			
			// Combine tracks and likes for analysis
			List<Map<String, Object>> allTracks = new ArrayList<Map<String, Object>>();
			if (tracks != null) allTracks.addAll(tracks);
			if (likes != null) allTracks.addAll(likes);
			
			java.time.LocalDate tenYearsAgo = java.time.LocalDate.now().minusYears(10);
			java.time.LocalDate twoYearsAgo = java.time.LocalDate.now().minusYears(2);
			
			for (Map<String, Object> track : allTracks) {
				Object createdAtObj = track.get("created_at");
				if (createdAtObj instanceof String createdAt) {
					// Parse SoundCloud date format: "2024/01/15 12:00:00 +0000"
					java.time.LocalDate trackDate = parseSoundCloudDate(createdAt);
					
					// Only count if date was successfully parsed (not null)
					if (trackDate != null) {
						totalTracksAnalyzed++;
						
						if (trackDate.isBefore(tenYearsAgo)) {
							oldTracks++;
						} else if (trackDate.isAfter(twoYearsAgo)) {
							newTracks++;
						}
					}
					// Skip tracks with unparseable dates (trackDate is null)
				}
			}
			
			// Build context for AI
			context.append("Music Taste Analysis:\n");
			context.append("- Classic Genre Indicators: ").append(classicGenreCount).append("\n");
			context.append("- Modern Genre Indicators: ").append(modernGenreCount).append("\n");
			if (totalTracksAnalyzed > 0) {
				context.append("- Old Tracks (10+ years): ").append(oldTracks).append("\n");
				context.append("- New Tracks (<2 years): ").append(newTracks).append("\n");
			}
			if (topGenres != null && !topGenres.isEmpty()) {
				context.append("- Top Genres: ").append(String.join(", ", topGenres.subList(0, Math.min(5, topGenres.size())))).append("\n");
			}
			
			// Create prompt for music age generation
			String prompt = String.format(
				"Based on this music listener's taste analysis, determine their 'music age' persona. " +
				"Are they an 'Old Soul' (preferring classic genres, older tracks, timeless music)? " +
				"Or are they 'Young at Heart' (preferring modern genres, new releases, cutting-edge sounds)? " +
				"Or perhaps they're 'Timeless' (balanced mix of old and new)? " +
				"Give them a creative title like 'The Old Soul', 'The Young at Heart', 'The Timeless Listener', etc. " +
				"and write 2-3 sentences describing their musical age personality. " +
				"Make it fun, personal, and celebrate their unique taste. " +
				"Use the genre and track data naturally in the description.\n\n" +
				"%s",
				context.toString()
			);

			// Use Groq to generate the music age persona
			String musicAge = getGroqDescription("Music Age", "persona", prompt);
			
			if (musicAge != null && !musicAge.trim().isEmpty()) {
				return musicAge.trim();
			}
		} catch (Exception e) {
			System.out.println("Error generating music age: " + e.getMessage());
		}

		// Fallback persona
		return "The Timeless Listener - Your music taste spans generations, finding beauty in both classic melodies and modern beats.";
	}
	
	/**
	 * Helper method to parse SoundCloud date format: "2024/01/15 12:00:00 +0000"
	 * Returns null if parsing fails instead of returning current date (which would skew results)
	 */
	private java.time.LocalDate parseSoundCloudDate(String dateStr) {
		try {
			// SoundCloud format: "2024/01/15 12:00:00 +0000"
			String datePart = dateStr.split(" ")[0]; // Get "2024/01/15"
			String[] parts = datePart.split("/");
			if (parts.length == 3) {
				int year = Integer.parseInt(parts[0]);
				int month = Integer.parseInt(parts[1]);
				int day = Integer.parseInt(parts[2]);
				// Validate date range (reasonable bounds)
				if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
					return java.time.LocalDate.of(year, month, day);
				}
			}
		} catch (Exception e) {
			// Parsing failed - return null to indicate invalid date
		}
		return null; // Return null instead of current date to avoid skewing calculations
	}

	//Helper methods for safe casting
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> castToMapList(Object obj) {
		if (obj instanceof List<?> list) {
			return list.stream()
					.filter(item -> item instanceof Map<?, ?>)
					.map(item -> (Map<String, Object>) item)
					.toList();
		}

		return List.of();
	}

	private List<String> castToStringList(Object obj) {
		if (obj instanceof List<?> list) {
			return list.stream()
					.filter(item -> item instanceof String)
					.map(item -> (String) item)
					.toList();
		}

		return List.of();
	}

	/**
     * Formats the raw summary from {@link #getFullWrappedSummary} into a
     * user-friendly "SoundCloud Wrapped"-style response. Includes numbered rankings,
     * grouped sections, and human-readable stats.
     *
     * @return             Map containing user-friendly Wrapped summary
     */
	public Map<String, Object> formattedWrappedSummary() {
		try {
		Map<String, Object> raw = getFullWrappedSummary();
		Map<String, Object> wrapped = new LinkedHashMap<String, Object>();
		Map<String, Object> profile = new LinkedHashMap<String, Object>();
			profile.put("username", raw.getOrDefault("username", "Unknown"));
			profile.put("accountAgeYears", raw.getOrDefault("accountAgeYears", 0));
			profile.put("followers", raw.getOrDefault("followers", 0));
			profile.put("tracksUploaded", raw.getOrDefault("tracksUploaded", 0));
			profile.put("playlistsCreated", raw.getOrDefault("playlistsCreated", 0));
		wrapped.put("profile", profile);

		List<Map<String, Object>> rawTopTracks = castToMapList(raw.getOrDefault("topTracks", List.of()));
		List<Map<String, Object>> rankedTracks = new ArrayList<Map<String, Object>>();
		int rank = 1;

		for (Map<String, Object> track : rawTopTracks) {
				try {
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("rank", rank++);
					entry.put("title", track.getOrDefault("title", "Unknown Track"));
					Object userObj = track.get("user");
					String artist = "Unknown Artist";
					if (userObj instanceof Map<?, ?>) {
						Object usernameObj = ((Map<?, ?>) userObj).get("username");
						if (usernameObj instanceof String) {
							artist = (String) usernameObj;
						}
					}
					entry.put("artist", artist);
					entry.put("playCount", track.getOrDefault("playback_count", 0));
			rankedTracks.add(entry);
				} catch (Exception e) {
					System.out.println("Error processing track: " + e.getMessage());
					// Skip this track and continue
				}
		}

		wrapped.put("topTracks", rankedTracks);

		List<String> rawTopArtists = castToStringList(raw.getOrDefault("topLikedArtists", List.of()));
		List<Map<String, Object>> rankedArtists = new ArrayList<Map<String, Object>>();
		rank = 1;

		for (String artist : rawTopArtists) {
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("rank", rank++);
			entry.put("artist", artist);
			rankedArtists.add(entry);
		}

		wrapped.put("topArtists", rankedArtists);

		List<Map<String, Object>> repostedTracks = castToMapList(raw.getOrDefault("topRepostedTracks", List.of()));
		wrapped.put("topRepostedTracks", repostedTracks);

		Map<String, Object> stats = new LinkedHashMap<String, Object>();
		stats.put("totalListeningHours", raw.getOrDefault("totalListeningHours", 0));
		// likesGiven is stored as a long in getFullWrappedSummary (line 3942), not a List
		// Handle both cases: if it's a Number (long) use it directly, if it's a List get its size
		Object likesGivenObj = raw.getOrDefault("likesGiven", 0L);
		long likesGiven = 0L;
		if (likesGivenObj instanceof Number) {
			likesGiven = ((Number) likesGivenObj).longValue();
		} else if (likesGivenObj instanceof List<?>) {
			likesGiven = ((List<?>) likesGivenObj).size();
		}
		stats.put("likesGiven", likesGiven);
		stats.put("tracksUploaded", raw.getOrDefault("tracksUploaded", 0));
		stats.put("commentsPosted", raw.getOrDefault("commentsPosted", 0));
		stats.put("booksYouCouldHaveRead", raw.getOrDefault("booksYouCouldHaveRead", 0));
		wrapped.put("stats", stats);
		wrapped.put("funFact", raw.getOrDefault("funFact", "Unable to load data"));
		wrapped.put("peakYear", raw.getOrDefault("peakYear", ""));
		wrapped.put("globalTasteComparison", raw.getOrDefault("globalTasteComparison", ""));

		// Add "Support the Underground" metric
		Object undergroundSupportObj = raw.getOrDefault("undergroundSupportPercentage", 0.0);
		double undergroundSupport = 0.0;
		if (undergroundSupportObj instanceof Number) {
			undergroundSupport = ((Number) undergroundSupportObj).doubleValue();
		}
		wrapped.put("undergroundSupportPercentage", Math.round(undergroundSupport * 10.0) / 10.0); // Round to 1 decimal

		// Generate "Year in Review" poetry
		String username = (String) raw.getOrDefault("username", "Music Lover");
		@SuppressWarnings("unchecked")
		List<String> topGenres = (List<String>) raw.getOrDefault("topGenres", List.of());
		String yearInReviewPoetry = generateYearInReviewPoetry(rankedTracks, topGenres, username);
		wrapped.put("yearInReviewPoetry", yearInReviewPoetry);

		// Phase 2: Add Trendsetter Score
		@SuppressWarnings("unchecked")
		Map<String, Object> trendsetterScore = (Map<String, Object>) raw.getOrDefault("trendsetterScore", new HashMap<String, Object>());
		wrapped.put("trendsetterScore", trendsetterScore);

		// Phase 2: Add Repost King/Queen Score
		@SuppressWarnings("unchecked")
		Map<String, Object> repostKingScore = (Map<String, Object>) raw.getOrDefault("repostKingScore", new HashMap<String, Object>());
		wrapped.put("repostKingScore", repostKingScore);

		// Phase 2: Add Sonic Archetype
		String sonicArchetype = (String) raw.getOrDefault("sonicArchetype", "");
		wrapped.put("sonicArchetype", sonicArchetype);

		// Add Music Age
		String musicAge = (String) raw.getOrDefault("musicAge", "");
		wrapped.put("musicAge", musicAge);

		List<String> stories = new ArrayList<String>();

		if (!rankedTracks.isEmpty()) {
			Map<String, Object> firstTrack = rankedTracks.get(0);
			stories.add("🎶 Your #1 track this year was \"" + firstTrack.get("title")
			+ "\" by " + firstTrack.get("artist") + ". You just couldn’t get enough of it!");
		}

		if (!rankedArtists.isEmpty()) {
			Map<String, Object> firstArtist = rankedArtists.get(0);
			stories.add("🔥 You vibed most with " + firstArtist.get("artist")
			+ " — clearly your top artist of the year.");
		}

		Object hours = stats.get("totalListeningHours");

		if (hours != null) {
			// Format hours to 2 decimal places max for readability
			double hoursValue = 0.0;
			if (hours instanceof Number) {
				hoursValue = ((Number) hours).doubleValue();
			} else {
				try {
					hoursValue = Double.parseDouble(hours.toString());
				} catch (NumberFormatException e) {
					hoursValue = 0.0;
				}
			}
			// Format to 2 decimal places and remove trailing zeros
			String formattedHours = String.format("%.2f", hoursValue);
			formattedHours = formattedHours.replaceAll("\\.?0+$", "");
			stories.add("⌛ You spent " + formattedHours + " hours listening — enough to binge whole seasons of your favorite shows!");
		}

		Object funFact = raw.get("funFact");

		if (funFact != null) {
			stories.add("💡 Fun fact: " + funFact.toString());
		}

		if (!rankedTracks.isEmpty()) {
			stories.add("✨ And it didn’t stop there. Thanks to Related Tracks, every play led you to fresh sounds perfectly tuned to your taste.");
		}

		wrapped.put("stories", stories);

		return wrapped;
		} catch (Exception e) {
			System.out.println("Error in formattedWrappedSummary: " + e.getMessage());
			// Return minimal wrapped data on error
			Map<String, Object> minimalWrapped = new LinkedHashMap<String, Object>();
			Map<String, Object> minimalProfile = new LinkedHashMap<String, Object>();
			minimalProfile.put("username", "Unknown");
			minimalProfile.put("accountAgeYears", 0);
			minimalProfile.put("followers", 0);
			minimalProfile.put("tracksUploaded", 0);
			minimalProfile.put("playlistsCreated", 0);
			minimalWrapped.put("profile", minimalProfile);
			minimalWrapped.put("topTracks", new ArrayList<Map<String, Object>>());
			minimalWrapped.put("topArtists", new ArrayList<Map<String, Object>>());
			minimalWrapped.put("topRepostedTracks", new ArrayList<Map<String, Object>>());
			Map<String, Object> minimalStats = new LinkedHashMap<String, Object>();
			minimalStats.put("totalListeningHours", 0);
			minimalStats.put("likesGiven", 0);
			minimalStats.put("tracksUploaded", 0);
			minimalStats.put("commentsPosted", 0);
			minimalStats.put("booksYouCouldHaveRead", 0);
			minimalWrapped.put("stats", minimalStats);
			minimalWrapped.put("funFact", "Unable to load data - please try again later");
			minimalWrapped.put("peakYear", "");
			minimalWrapped.put("globalTasteComparison", "");
			minimalWrapped.put("stories", new ArrayList<String>());
			return minimalWrapped;
		}
	}
}