package com.soundwrapped.service;

import com.soundwrapped.exception.*;
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
import java.time.ZonedDateTime;
import java.util.*;

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

	private final TokenStore tokenStore;
	private final RestTemplate restTemplate;

	public SoundWrappedService(TokenStore tokenStore, RestTemplate restTemplate) {
		this.tokenStore = tokenStore;
		this.restTemplate = restTemplate;
	}

	public TokenStore getTokenStore() {
		return tokenStore;
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
		HttpEntity<String> request = new HttpEntity<String>(headers);
		
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate
					.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>(){});

			Map<String, Object> responseBody = response.getBody();
			return responseBody != null ? responseBody : Map.of();
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

			//Always persist through TokenStore
			tokenStore.saveTokens(newAccessToken, newRefreshToken != null ? newRefreshToken : refreshToken);

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

			tokenStore.saveTokens(
					(String) responseBody.get("access_token"),
					(String) responseBody.get("refresh_token"));

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
	 * 
	 * @return JSON response body as a {@code List} of {@code Map}s
	 */
	private List<Map<String, Object>> fetchPaginatedResultsWithRefresh(String url) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (url != null) {
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
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequestWithRefresh(url);
	}

	/**
     * Retrieves the user's liked tracks.
     * 
     * @return {@code List} of liked tracks
     */
	public List<Map<String, Object>> getUserLikes() {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return fetchPaginatedResultsWithRefresh(url);
    }

	/**
     * Retrieves the user's playlists.
     * 
     * @return {@code List} of playlists
     */
	public List<Map<String, Object>> getUserPlaylists() {
		String url = soundCloudApiBaseUrl + urlExtension("/me/playlists", 50);
		
		return fetchPaginatedResultsWithRefresh(url);
    }

	/**
     * Retrieves the user's followers.
     * 
     * @return {@code List} of followers
     */
	public List<Map<String, Object>> getUserFollowers() {
		String url = soundCloudApiBaseUrl + urlExtension("/me/followers", 50);

		return fetchPaginatedResultsWithRefresh(url);
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
		Map<String, Object> profile = getUserProfile();
		List<Map<String, Object>> likes = getUserLikes();
		List<Map<String, Object>> tracks = getUserTracks();
		List<Map<String, Object>> playlists = getUserPlaylists();
		List<Map<String, Object>> followers = getUserFollowers();

		//Profile-level statistics
		wrapped.put("username", profile.get("username"));
		wrapped.put("fullName", profile.get("full_name"));
		wrapped.put("followers", profile.get("followers_count"));
		wrapped.put("following", profile.get("followings_count"));
		wrapped.put("likesGiven", profile.get("public_favorites_count"));
		wrapped.put("reposts", profile.get("reposts_count"));
		wrapped.put("tracksUploaded", profile.get("track_count"));
		wrapped.put("playlistsCreated", profile.get("playlist_count"));
		wrapped.put("commentsPosted", profile.get("comments_count"));
		wrapped.put("remainingUploadQuotaSeconds", profile.get("upload_seconds_left"));

		String createdAt = (String) profile.get("created_at");

		if (createdAt != null) {
			wrapped.put("accountAgeYears", calculateAccountAgeYears(createdAt));
		}

		long totalDurationMs = tracks.stream()
				.mapToLong(track -> ((Number) track.getOrDefault("duration", 0))
				.longValue()).sum();
		double totalDurationHours = totalDurationMs / 1000.0 / 60.0 / 60.0;
		wrapped.put("totalListeningHours", totalDurationHours);

		//Assuming reading speed of 50 pages/book and book length of 300 pages
		int estimatedBooksRead = (int) (totalDurationHours * 50 / 300);
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
		Map<String, Object> raw = getFullWrappedSummary();
		Map<String, Object> wrapped = new LinkedHashMap<String, Object>();
		Map<String, Object> profile = new LinkedHashMap<String, Object>();
		profile.put("username", raw.get("username"));
		profile.put("accountAgeYears", raw.get("accountAgeYears"));
		profile.put("followers", raw.get("followers"));
		profile.put("tracksUploaded", raw.get("tracksUploaded"));
		profile.put("playlistsCreated", raw.get("playlistsCreated"));
		wrapped.put("profile", profile);

		List<Map<String, Object>> rawTopTracks = castToMapList(raw.getOrDefault("topTracks", List.of()));
		List<Map<String, Object>> rankedTracks = new ArrayList<Map<String, Object>>();
		int rank = 1;

		for (Map<String, Object> track : rawTopTracks) {
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("rank", rank++);
			entry.put("title", track.get("title"));
			entry.put("artist", ((Map<?, ?>) track.get("user")).get("username"));
			entry.put("playCount", track.get("playback_count"));
			rankedTracks.add(entry);
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
		stats.put("totalListeningHours", raw.get("totalListeningHours"));
		stats.put("likesGiven", ((List<?>) raw.getOrDefault("likesGiven", List.of())).size());
		stats.put("tracksUploaded", raw.get("tracksUploaded"));
		stats.put("commentsPosted", raw.get("commentsPosted"));
		stats.put("booksYouCouldHaveRead", raw.get("booksYouCouldHaveRead"));
		wrapped.put("stats", stats);
		wrapped.put("funFact", raw.get("funFact"));
		wrapped.put("peakYear", raw.get("peakYear"));
		wrapped.put("globalTasteComparison", raw.get("globalTasteComparison"));

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
	}
}