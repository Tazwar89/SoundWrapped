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

	private final TokenStore tokenStore;
	private final RestTemplate restTemplate;
	private final GenreAnalysisService genreAnalysisService;
	private final UserActivityRepository userActivityRepository;
	private final ActivityTrackingService activityTrackingService;
	
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
			ActivityTrackingService activityTrackingService) {
		this.tokenStore = tokenStore;
		this.restTemplate = restTemplate;
		this.genreAnalysisService = genreAnalysisService;
		this.userActivityRepository = userActivityRepository;
		this.activityTrackingService = activityTrackingService;
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
					Map<String, Object> result = new HashMap<>();
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
		} catch (Exception e) {
			System.out.println("Error fetching user profile: " + e.getMessage());
			// Return empty profile on error
			Map<String, Object> errorProfile = new HashMap<>();
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
			return new ArrayList<>();
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
			return new ArrayList<>();
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
			return new ArrayList<>();
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
			return new ArrayList<>();
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
		List<Map<String, Object>> activities = new ArrayList<>();
		
		// Get user ID for querying tracked activities
		String userId = "";
		try {
			Map<String, Object> profile = getUserProfile();
			userId = String.valueOf(profile.getOrDefault("id", ""));
		} catch (Exception e) {
			System.err.println("Error getting user profile for recent activity: " + e.getMessage());
		}
		
		// Build a map of trackId -> most recent LIKE activity timestamp from database
		Map<String, java.time.LocalDateTime> trackedLikeTimestamps = new HashMap<>();
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
		Map<String, java.time.LocalDateTime> trackedRepostTimestamps = new HashMap<>();
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
							Map<String, Object> activity = new HashMap<>();
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
							Map<String, Object> activity = new HashMap<>();
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
							Map<String, Object> activity = new HashMap<>();
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
			return new ArrayList<>();
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
			Map<String, Long> trackPlayCounts = new HashMap<>();
			for (Object[] result : mostPlayed) {
				String trackId = String.valueOf(result[0]);
				Long playCount = ((Number) result[1]).longValue();
				trackPlayCounts.put(trackId, playCount);
			}
			
			// Fetch track details from SoundCloud API for each tracked track
			List<Map<String, Object>> topTracks = new ArrayList<>();
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
	private List<Map<String, Object>> getTracksFromPlaylist(String playlistUrl) {
		try {
			String accessToken = getAccessTokenForRequest();
			if (accessToken == null) {
				System.err.println("No access token available for playlist request. User may need to authenticate.");
				return new ArrayList<>();
			}
			
			// Try alternative approach: extract username and set name from URL
			// URL format: https://soundcloud.com/{username}/sets/{set-name}
			// For example: https://soundcloud.com/music-charts-us/sets/all-music-genres
			String username = null;
			String setName = null;
			try {
				java.net.URL url = new java.net.URL(playlistUrl);
				String path = url.getPath();
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
				return new ArrayList<>();
			} catch (Exception e) {
				System.err.println("Exception resolving playlist " + playlistUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<>();
			}
			
			System.out.println("Resolve response status: " + resolveResponse.getStatusCode());
			Map<String, Object> playlist = resolveResponse.getBody();
			if (playlist == null) {
				System.err.println("Playlist resolve returned null for: " + playlistUrl);
				System.err.println("Response status: " + resolveResponse.getStatusCode());
				return new ArrayList<>();
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
				return new ArrayList<>();
			}
			
			// Get playlist ID
			Object playlistIdObj = playlist.get("id");
			if (playlistIdObj == null) {
				System.err.println("Playlist ID not found in response for: " + playlistUrl);
				System.err.println("Playlist response keys: " + playlist.keySet());
				return new ArrayList<>();
			}
			
			String playlistId = String.valueOf(playlistIdObj);
			System.out.println("Resolved playlist ID: " + playlistId);
			
			return getTracksFromPlaylistById(playlistId, accessToken);
		} catch (Exception e) {
			System.err.println("Error fetching tracks from playlist " + playlistUrl + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<>();
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
			// Fetch tracks from playlist - try as paginated response first
			String tracksUrl = soundCloudApiBaseUrl + "/playlists/" + playlistId + "/tracks?limit=50&linked_partitioning=true";
			System.out.println("Fetching tracks from: " + tracksUrl);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			headers.set("User-Agent", "SoundWrapped/1.0 (https://github.com/tazwarsikder/SoundWrapped)");
			headers.set("Accept", "application/json");
			HttpEntity<String> request = new HttpEntity<String>(headers);
			
			// Try as paginated response with collection field
			ResponseEntity<Map<String, Object>> tracksResponse = restTemplate.exchange(
				tracksUrl,
				HttpMethod.GET,
				request,
				new ParameterizedTypeReference<Map<String, Object>>(){}
			);
			
			System.out.println("Tracks API response status: " + tracksResponse.getStatusCode());
			
			if (tracksResponse.getStatusCode().is2xxSuccessful() && tracksResponse.getBody() != null) {
				Map<String, Object> responseBody = tracksResponse.getBody();
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
					System.out.println("Successfully fetched " + tracks.size() + " tracks from playlist ID: " + playlistId);
					return tracks;
				} else {
					System.err.println("No tracks found in response for playlist " + playlistId);
					System.err.println("Response body keys: " + responseBody.keySet());
				}
			} else {
				System.err.println("Failed to fetch tracks from playlist " + playlistId);
				System.err.println("Response status: " + tracksResponse.getStatusCode());
			}
			
			return new ArrayList<>();
		} catch (Exception e) {
			System.err.println("Error fetching tracks from playlist ID " + playlistId + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<>();
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
					List<Map<String, Object>> playlistTracksFromResponse = new ArrayList<>();
					
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
				return new ArrayList<>();
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
				return new ArrayList<>();
			}
			
			// Extract tracks from paginated response
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> tracks = (List<Map<String, Object>>) responseBody.get("collection");
			if (tracks == null || tracks.isEmpty()) {
				System.err.println("Fallback method: No tracks in collection from SoundCloud API");
				System.err.println("Response keys: " + responseBody.keySet());
				return new ArrayList<>();
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
			return new ArrayList<>();
		}
	}

	/**
	 * Gets a featured track from trending/popular tracks.
	 * Selects the top trending track (highest trending score) as the featured track.
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
			
			List<Map<String, Object>> popularTracks = getPopularTracks(20);
			if (!popularTracks.isEmpty()) {
				// Use date-based seed to select a track consistently throughout the day
				long seed = today.toEpochDay();
				Random random = new Random(seed);
				int selectedIndex = random.nextInt(Math.min(popularTracks.size(), 10)); // Select from top 10
				Map<String, Object> selectedTrack = popularTracks.get(selectedIndex);
				
				// Cache the result for today
				cachedSongOfTheDay = selectedTrack;
				cachedSongDate = today;
				
				System.out.println("Song of the day: " + selectedTrack.get("title") + " (cached for " + today + ")");
				return selectedTrack;
			}
		} catch (Exception e) {
			System.out.println("Error fetching featured track: " + e.getMessage());
		}
		return new HashMap<>();
	}

	/**
	 * Gets a featured artist from trending/popular tracks.
	 * Selects the artist with the highest trending score across their tracks.
	 * 
	 * @return A featured artist or empty map if none available
	 */
	public Map<String, Object> getFeaturedArtist() {
		System.out.println("getFeaturedArtist() method called");
		try {
			// Check if we have a cached artist for today
			LocalDate today = LocalDate.now();
			if (cachedArtistOfTheDay != null && cachedArtistDate != null && cachedArtistDate.equals(today)) {
				System.out.println("Returning cached artist of the day: " + cachedArtistOfTheDay.get("username") + " (cached on " + cachedArtistDate + ")");
				return cachedArtistOfTheDay;
			}
			
			List<Map<String, Object>> popularTracks = getPopularTracks(50);
			System.out.println("getFeaturedArtist: Retrieved " + popularTracks.size() + " popular tracks");
			if (!popularTracks.isEmpty()) {
				// Extract unique artists with their trending scores
				Map<String, Map<String, Object>> artistMap = new HashMap<>();
				Map<String, Double> artistTrendingScores = new HashMap<>();
				
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
					String artistDescription = getArtistDescription(selectedArtist);
					
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
						artistTracks = new ArrayList<>();
					}
					
					System.out.println("Final result: getTracksFromArtist returned " + artistTracks.size() + " tracks");
					
					// Create result with artist, description, and tracks
					Map<String, Object> result = new HashMap<>(selectedArtist);
					result.put("description", artistDescription);
					result.put("tracks", artistTracks);
					
					// Cache the result for today
					cachedArtistOfTheDay = result;
					cachedArtistDate = today;
					
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
		return new HashMap<>();
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
				"wave", "electronic", "hip-hop", "indie", "rock", "pop", 
				"house", "techno", "dubstep", "ambient", "jazz", "r&b",
				"alternative", "folk", "country", "metal", "punk", "reggae",
				// Obscure subgenres
				"indietronica", "future garage", "vaporwave", "synthwave",
				"drum & bass", "rage", "lo-fi", "chillwave", "shoegaze",
				"post-rock", "math rock", "hyperpop", "neo-soul",
				"afrobeats", "phonk", "dancehall", "grime", "drill"
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
			
			Map<String, Object> result = new HashMap<>();
			result.put("genre", selectedGenre);
			result.put("description", genreDescription);
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
			Map<String, Object> result = new HashMap<>();
			result.put("genre", "electronic");
			result.put("description", "Electronic music encompasses a wide range of genres that primarily use electronic instruments and technology.");
			result.put("tracks", new ArrayList<>());
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
				return new ArrayList<>();
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
			
			if (resolveResponse.getStatusCode().is2xxSuccessful() && resolveResponse.getBody() != null) {
				Map<String, Object> resolved = resolveResponse.getBody();
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
					return new ArrayList<>();
				}
				
				Map<String, Object> responseBody = response.getBody();
				if (responseBody == null) {
					System.err.println("No response body from genre tag endpoint");
					return new ArrayList<>();
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
							String genreNormalized = genreLower.replaceAll("[\\s-]", "");
							List<Map<String, Object>> filteredDirectTracks = new ArrayList<>();
							
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
								
								// Check if genre name appears in tags
								String[] tags = tagList.split(",");
								boolean genreFound = false;
								for (String tag : tags) {
									String normalizedTag = tag.trim().toLowerCase();
									String tagNormalized = normalizedTag.replaceAll("[\\s-]", "");
									
									// Use exact match on normalized strings to avoid false positives
									if (normalizedTag.equals(genreLower) || tagNormalized.equals(genreNormalized)) {
										genreFound = true;
										break;
									}
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
					return new ArrayList<>();
				}
				
				System.out.println("Found " + tracks.size() + " tracks in collection");
				
				// Filter tracks: English titles only and genre must be in tags
				String genreLower = genreName.toLowerCase();
				String genreNormalized = genreLower.replaceAll("[\\s-]", "");
				List<Map<String, Object>> filteredTracks = new ArrayList<>();
				
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
					
					// Check if genre name appears in tags
					String[] tags = tagList.split(",");
					boolean genreFound = false;
					for (String tag : tags) {
						String normalizedTag = tag.trim().toLowerCase();
						String tagNormalized = normalizedTag.replaceAll("[\\s-]", "");
						
						// Use exact match on normalized strings to avoid false positives
						if (normalizedTag.equals(genreLower) || tagNormalized.equals(genreNormalized)) {
							genreFound = true;
							break;
						}
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
				return new ArrayList<>();
			} catch (Exception e) {
				System.err.println("Error fetching tracks for genre " + genreName + ": " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<>();
			}
		} catch (Exception e) {
			System.err.println("Error fetching tracks from genre tag " + genreName + ": " + e.getMessage());
			e.printStackTrace();
			return new ArrayList<>();
		}
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
	 * Gets a description for a music genre.
	 * First tries to fetch from Google Knowledge Graph API for obscure subgenres.
	 * Falls back to hardcoded descriptions for well-known genres.
	 * 
	 * @param genreName The name of the genre
	 * @return A description of the genre
	 */
	private String getGenreDescription(String genreName) {
		// First, try to get description from Google Knowledge Graph API
		// This is especially useful for obscure subgenres like "indietronica", "wave", "future garage"
		String kgDescription = getGoogleKnowledgeGraphDescription(genreName + " music genre");
		if (kgDescription != null && !kgDescription.trim().isEmpty()) {
			return kgDescription;
		}
		
		// Fallback to hardcoded descriptions for well-known genres
		Map<String, String> genreDescriptions = new HashMap<>();
		genreDescriptions.put("wave", "Wave is a subgenre of electronic music characterized by its atmospheric, ethereal soundscapes and emotional melodies. Often featuring reverb-drenched synths and haunting vocals, wave music creates a dreamy, introspective listening experience.");
		genreDescriptions.put("electronic", "Electronic music encompasses a wide range of genres that primarily use electronic instruments and technology. From ambient soundscapes to high-energy dance tracks, electronic music continues to evolve and influence modern music culture.");
		genreDescriptions.put("hip-hop", "Hip-hop is a cultural movement and music genre that emerged in the 1970s. Characterized by rhythmic speech (rapping), DJing, breakdancing, and graffiti art, hip-hop has become one of the most influential music genres worldwide.");
		genreDescriptions.put("indie", "Indie music, short for independent, refers to music produced independently from major commercial record labels. Indie artists often explore creative freedom, resulting in diverse and innovative sounds across various subgenres.");
		genreDescriptions.put("rock", "Rock music is a broad genre of popular music that originated in the 1950s. Characterized by a strong beat, electric guitars, and powerful vocals, rock has spawned countless subgenres and remains a dominant force in music.");
		genreDescriptions.put("pop", "Pop music is a genre of popular music that originated in its modern form during the mid-1950s. Known for its catchy melodies, accessible lyrics, and broad appeal, pop music dominates mainstream charts and radio.");
		genreDescriptions.put("house", "House music is a genre of electronic dance music that originated in Chicago in the early 1980s. Characterized by repetitive 4/4 beats, synthesized basslines, and soulful vocals, house music is a foundation of modern dance culture.");
		genreDescriptions.put("techno", "Techno is a form of electronic dance music that emerged in Detroit in the mid-1980s. Known for its repetitive, mechanical beats and futuristic soundscapes, techno emphasizes rhythm and texture over melody.");
		genreDescriptions.put("dubstep", "Dubstep is a genre of electronic dance music that originated in South London in the early 2000s. Characterized by heavy basslines, syncopated rhythms, and sparse, minimal arrangements, dubstep has influenced many modern electronic genres.");
		genreDescriptions.put("ambient", "Ambient music is a genre that emphasizes tone and atmosphere over traditional musical structure. Often instrumental and minimal, ambient music creates immersive soundscapes perfect for relaxation and contemplation.");
		genreDescriptions.put("jazz", "Jazz is a music genre that originated in African American communities in New Orleans. Known for its improvisation, syncopation, and swing rhythms, jazz has evolved into many subgenres and remains a vital form of musical expression.");
		genreDescriptions.put("r&b", "R&B (Rhythm and Blues) is a genre of popular music that originated in African American communities in the 1940s. Combining elements of jazz, gospel, and blues, R&B has influenced countless artists and continues to evolve.");
		genreDescriptions.put("alternative", "Alternative rock is a genre of rock music that emerged in the 1980s as an alternative to mainstream rock. Known for its diverse sounds, experimental approaches, and independent spirit, alternative rock has produced many influential artists.");
		genreDescriptions.put("folk", "Folk music is a genre that encompasses traditional music passed down through generations, as well as contemporary music inspired by these traditions. Characterized by acoustic instruments and storytelling lyrics, folk music connects listeners to cultural heritage.");
		genreDescriptions.put("country", "Country music is a genre that originated in the rural Southern United States. Known for its storytelling lyrics, twangy guitars, and themes of love, loss, and everyday life, country music has a rich history and devoted fanbase.");
		genreDescriptions.put("metal", "Metal is a genre of rock music characterized by heavy, distorted guitars, powerful vocals, and aggressive rhythms. With numerous subgenres ranging from classic heavy metal to extreme forms, metal has a passionate global following.");
		genreDescriptions.put("punk", "Punk rock is a music genre that emerged in the mid-1970s as a reaction against mainstream rock. Known for its fast tempos, short songs, and anti-establishment lyrics, punk has influenced countless artists and movements.");
		genreDescriptions.put("reggae", "Reggae is a music genre that originated in Jamaica in the late 1960s. Characterized by its distinctive rhythm, offbeat accents, and socially conscious lyrics, reggae has spread worldwide and influenced many other genres.");
		
		// Check hardcoded descriptions first
		String hardcoded = genreDescriptions.get(genreName.toLowerCase());
		if (hardcoded != null) {
			return hardcoded;
		}
		
		// Final fallback: generic description
		return genreName.substring(0, 1).toUpperCase() + genreName.substring(1) + " is a diverse and evolving music genre with a rich history and dedicated fanbase.";
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
		String username = (String) artist.getOrDefault("username", "");
		String fullName = (String) artist.getOrDefault("full_name", "");
		String descriptionText = (String) artist.getOrDefault("description", "");
		Object followersCountObj = artist.get("followers_count");
		long followersCount = followersCountObj instanceof Number ? ((Number) followersCountObj).longValue() : 0;
		Object trackCountObj = artist.get("track_count");
		long trackCount = trackCountObj instanceof Number ? ((Number) trackCountObj).longValue() : 0;
		
		// Determine search terms for Wikipedia check
		// Try username first, then fullName, as username is more likely to match Wikipedia
		String searchTerm = username != null && !username.isEmpty() ? username : 
			(fullName != null && !fullName.isEmpty() ? fullName : null);
		
		// Check if artist meets the new criteria:
		// 1. >= 10000 followers
		boolean hasEnoughFollowers = followersCount >= 10000;
		
		// 2. Has Wikipedia entry
		boolean hasWikipediaEntry = false;
		if (searchTerm != null) {
			hasWikipediaEntry = checkWikipediaEntry(searchTerm);
		}
		
		// 3. Has Google Search About section (checked via Google Knowledge Graph API)
		boolean hasGoogleAboutSection = false;
		if (searchTerm != null) {
			hasGoogleAboutSection = checkGoogleKnowledgeGraph(searchTerm);
		}
		
		// Only proceed if at least one condition is met
		if (!hasEnoughFollowers && !hasWikipediaEntry && !hasGoogleAboutSection) {
			// Artist doesn't meet any of the criteria for verified public information
			return null;
		}
		
		// PRIORITY ORDER: Wikipedia > Google Knowledge Graph > SoundCloud Bio > Generated Description
		
		// 1. Try Wikipedia description first (highest priority)
		if (hasWikipediaEntry && searchTerm != null) {
			String wikipediaDescription = getWikipediaDescription(searchTerm);
			if (wikipediaDescription != null && !wikipediaDescription.trim().isEmpty()) {
				return wikipediaDescription;
			}
		}
		
		// 2. Try Google Knowledge Graph description (second priority)
		if (hasGoogleAboutSection && searchTerm != null) {
			String kgDescription = getGoogleKnowledgeGraphDescription(searchTerm);
			if (kgDescription != null && !kgDescription.trim().isEmpty()) {
				return kgDescription;
			}
		}
		
		// 3. Use the artist's SoundCloud bio if available and it's substantial (not just a link or placeholder)
		if (descriptionText != null && !descriptionText.trim().isEmpty()) {
			// Check if it's a meaningful description (not just a URL or very short)
			String trimmed = descriptionText.trim();
			if (trimmed.length() > 50 && !trimmed.toLowerCase().startsWith("http") &&
				!trimmed.matches(".*https?://[^\\s]+.*")) {
				// Truncate if too long, but keep it informative
				if (trimmed.length() > 250) {
					// Try to truncate at a sentence boundary
					int lastPeriod = trimmed.lastIndexOf('.', 250);
					if (lastPeriod > 100) {
						return trimmed.substring(0, lastPeriod + 1);
					}
					return trimmed.substring(0, 247) + "...";
				}
				return trimmed;
			}
		}
		
		// 4. Generate an AI-style description as final fallback (only if we have verified info)
		StringBuilder description = new StringBuilder();
		String displayName = username != null && !username.isEmpty() ? 
			username.substring(0, 1).toUpperCase() + username.substring(1) : 
			(fullName != null && !fullName.isEmpty() ? fullName : "This artist");
		
		description.append(displayName);
		
		// Build a comprehensive description based on available stats
		if (followersCount >= 1000000) {
			description.append(" is a prominent artist on SoundCloud with over ")
				.append(followersCount / 1000000).append(" million followers");
		} else if (followersCount >= 100000) {
			description.append(" is a well-established artist on SoundCloud with over ")
				.append(followersCount / 1000).append("k followers");
		} else if (followersCount >= 10000) {
			description.append(" is an emerging artist on SoundCloud with over ")
				.append(followersCount / 1000).append("k followers");
		} else {
			// If we're here, they have Wikipedia or Google About section
			if (hasWikipediaEntry) {
				description.append(" is a notable artist");
			} else {
				description.append(" is a talented artist");
			}
		}
		
		if (trackCount > 0) {
			if (trackCount >= 100) {
				description.append(" and has released over ").append(trackCount).append(" tracks");
			} else if (trackCount >= 50) {
				description.append(" with ").append(trackCount).append(" tracks");
			} else if (trackCount >= 20) {
				description.append(" and has released ").append(trackCount).append(" tracks");
			}
		}
		
		description.append(". Explore their popular tracks and discover new sounds.");
		
		return description.toString();
	}
	
	/**
	 * Checks if an artist has a Wikipedia entry using the Wikipedia API.
	 * Uses the search API to find matching articles.
	 * 
	 * @param searchTerm The artist's name to search for
	 * @return true if a Wikipedia entry exists, false otherwise
	 */
	private boolean checkWikipediaEntry(String searchTerm) {
		try {
			// Wikipedia API endpoint for searching
			String encodedSearch = java.net.URLEncoder.encode(searchTerm, "UTF-8");
			String wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedSearch;
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Agent", "SoundWrapped/1.0 (https://soundwrapped.com; contact@example.com)");
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			HttpEntity<String> request = new HttpEntity<>(headers);
			
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
		java.util.List<String> searchVariations = new java.util.ArrayList<>();
		
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
		java.util.Set<String> uniqueVariations = new java.util.LinkedHashSet<>(searchVariations);
		
		for (String variation : uniqueVariations) {
			try {
				String encodedSearch = java.net.URLEncoder.encode(variation, "UTF-8");
				String wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedSearch;
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("User-Agent", "SoundWrapped/1.0 (https://soundwrapped.com; contact@example.com)");
				headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				HttpEntity<String> request = new HttpEntity<>(headers);
				
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
			HttpEntity<String> request = new HttpEntity<>(headers);
			
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
			HttpEntity<String> request = new HttpEntity<>(headers);
			
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
				return new ArrayList<>();
			}
			System.out.println("Access token obtained (length: " + (accessToken != null ? accessToken.length() : 0) + ")");
			
			System.out.println("Fetching tracks from artist: " + artistPermalink);
			
			if (artistPermalink == null || artistPermalink.isEmpty()) {
				System.err.println("Artist permalink is null or empty, cannot fetch tracks");
				return new ArrayList<>();
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
			return new ArrayList<>();
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
				return new ArrayList<>();
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
				return new ArrayList<>();
			} catch (Exception e) {
				System.err.println("Exception resolving popular-tracks URL: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<>();
			}
			
			System.out.println("Resolve response status: " + resolveResponse.getStatusCode());
			
			if (resolveResponse.getStatusCode().is2xxSuccessful() && resolveResponse.getBody() != null) {
				Map<String, Object> resolved = resolveResponse.getBody();
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
						
						if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
							Map<String, Object> responseBody = response.getBody();
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
			
			return new ArrayList<>();
			
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromPopularTracksUrl for permalink: " + artistPermalink);
			System.err.println("Error: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
			return new ArrayList<>();
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
				return new ArrayList<>();
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
				
				if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
					Map<String, Object> responseBody = response.getBody();
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
			
			return new ArrayList<>();
			
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("EXCEPTION in getTracksFromArtistByUserId for user ID: " + userId);
			System.err.println("Error: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
			return new ArrayList<>();
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
				return new ArrayList<>();
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
				return new ArrayList<>();
			} catch (Exception e) {
				System.err.println("Fallback: Exception resolving artist profile: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				e.printStackTrace();
				return new ArrayList<>();
			}
			
			System.out.println("Fallback: Resolve response status: " + resolveResponse.getStatusCode());
			
			if (resolveResponse.getStatusCode().is2xxSuccessful() && resolveResponse.getBody() != null) {
				Map<String, Object> resolved = resolveResponse.getBody();
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
							
							if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
								Map<String, Object> responseBody = response.getBody();
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
							
							if (searchResponse.getStatusCode().is2xxSuccessful() && searchResponse.getBody() != null) {
								Map<String, Object> searchBody = searchResponse.getBody();
								System.out.println("Fallback: Approach 2 - Search response body keys: " + searchBody.keySet());
								@SuppressWarnings("unchecked")
								List<Map<String, Object>> searchTracks = (List<Map<String, Object>>) searchBody.get("collection");
								
								if (searchTracks != null && !searchTracks.isEmpty()) {
									// Filter tracks to only include those by this artist
									List<Map<String, Object>> artistTracks = new ArrayList<>();
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
		return new ArrayList<>();
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
			Map<String, Integer> genreCounts = new HashMap<>();
			Map<String, Double> genrePopularityScores = new HashMap<>();
			
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
		Map<String, Object> profile = new HashMap<>();
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
		List<Map<String, Object>> likes = new ArrayList<>();
		try {
			Thread.sleep(500); // 500ms delay
			likes = getUserLikes();
		} catch (Exception e) {
			System.out.println("Failed to fetch likes: " + e.getMessage());
		}
		
		List<Map<String, Object>> tracks = new ArrayList<>();
		try {
			Thread.sleep(500); // 500ms delay
			tracks = getUserTracks();
		} catch (Exception e) {
			System.out.println("Failed to fetch tracks: " + e.getMessage());
		}
		
		List<Map<String, Object>> playlists = new ArrayList<>();
		try {
			Thread.sleep(500); // 500ms delay
			playlists = getUserPlaylists();
		} catch (Exception e) {
			System.out.println("Failed to fetch playlists: " + e.getMessage());
		}
		
		List<Map<String, Object>> followers = new ArrayList<>();
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

		//Top 5 tracks by play count
		List<Map<String, Object>> topTracks = tracks.stream()
				.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("playback_count", 0)).longValue(),
						((Number) a.getOrDefault("playback_count", 0)).longValue()))
				.limit(5).toList();
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

		return wrapped;
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
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("rank", rank++);
			entry.put("artist", artist);
			rankedArtists.add(entry);
		}

		wrapped.put("topArtists", rankedArtists);

		List<Map<String, Object>> repostedTracks = castToMapList(raw.getOrDefault("topRepostedTracks", List.of()));
		wrapped.put("topRepostedTracks", repostedTracks);

		Map<String, Object> stats = new LinkedHashMap<String, Object>();
		stats.put("totalListeningHours", raw.getOrDefault("totalListeningHours", 0));
		stats.put("likesGiven", ((List<?>) raw.getOrDefault("likesGiven", List.of())).size());
		stats.put("tracksUploaded", raw.getOrDefault("tracksUploaded", 0));
		stats.put("commentsPosted", raw.getOrDefault("commentsPosted", 0));
		stats.put("booksYouCouldHaveRead", raw.getOrDefault("booksYouCouldHaveRead", 0));
		wrapped.put("stats", stats);
		wrapped.put("funFact", raw.getOrDefault("funFact", "Unable to load data"));
		wrapped.put("peakYear", raw.getOrDefault("peakYear", ""));
		wrapped.put("globalTasteComparison", raw.getOrDefault("globalTasteComparison", ""));

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
			stories.add("⌛ You spent " + hours + " hours listening — enough to binge whole seasons of your favorite shows!");
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
			Map<String, Object> minimalWrapped = new LinkedHashMap<>();
			Map<String, Object> minimalProfile = new LinkedHashMap<>();
			minimalProfile.put("username", "Unknown");
			minimalProfile.put("accountAgeYears", 0);
			minimalProfile.put("followers", 0);
			minimalProfile.put("tracksUploaded", 0);
			minimalProfile.put("playlistsCreated", 0);
			minimalWrapped.put("profile", minimalProfile);
			minimalWrapped.put("topTracks", new ArrayList<>());
			minimalWrapped.put("topArtists", new ArrayList<>());
			minimalWrapped.put("topRepostedTracks", new ArrayList<>());
			Map<String, Object> minimalStats = new LinkedHashMap<>();
			minimalStats.put("totalListeningHours", 0);
			minimalStats.put("likesGiven", 0);
			minimalStats.put("tracksUploaded", 0);
			minimalStats.put("commentsPosted", 0);
			minimalStats.put("booksYouCouldHaveRead", 0);
			minimalWrapped.put("stats", minimalStats);
			minimalWrapped.put("funFact", "Unable to load data - please try again later");
			minimalWrapped.put("peakYear", "");
			minimalWrapped.put("globalTasteComparison", "");
			minimalWrapped.put("stories", new ArrayList<>());
			return minimalWrapped;
		}
	}
}