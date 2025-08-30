package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
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
public class SoundCloudService {
	@Value("${soundcloud.api.base-url:https://api.soundcloud.com}")
	private String soundCloudApiBaseUrl;

	@Value("${soundcloud.client-id}")
	private String clientId;

	@Value("${soundcloud.client-secret}")
	private String clientSecret;

	private final RestTemplate restTemplate = new RestTemplate();

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
		ResponseEntity<Map<String, Object>> response = restTemplate
				.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>(){});

		return response.getBody();
	}

	/**
     * Send authenticated HTTP GET request with automatic token refresh.
     * If the access token has expired, it is refreshed using the refresh token.
     *
     * @param refreshToken Valid OAuth2 refresh token for fallback
     */
	private Map<String, Object> makeGetRequestWithRefresh(
			String url, String accessToken, String refreshToken) {
		try {
			return makeGetRequest(url, accessToken);
		}

		catch (HttpClientErrorException htee) {
			if (htee.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				//Access token expired → refresh it
				String newToken = refreshAccessToken(refreshToken);

				//Optionally update stored access token in DB here
				return makeGetRequest(url, newToken);
			}

			throw htee;
		}
	}

	/**
	 * Refreshes the access token using the given refresh token.
	 * 
	 * @return New access token
	 */
	private String refreshAccessToken(String refreshToken) {
		String url = "https://api.soundcloud.com/oauth2/token";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		Map<String, String> body = new HashMap<String, String>();
		body.put("grant_type", "refresh_token");
		body.put("refresh_token", refreshToken);

		HttpEntity<Map<String, String>> request = new HttpEntity<Map<String, String>>(body, headers);
		ResponseEntity<Map<String, Object>> response = restTemplate
				.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>(){});

		return (String) response.getBody().get("access_token");
	}

	/**
	 * "Overloaded" version of makeGetRequest to send GET requests for endpoints
	 * returning paginated JSON arrays.
	 * 
	 * @return JSON response body as a {@code List} of {@code Map}s
	 */
	private List<Map<String, Object>> fetchPaginatedResultsWithRefresh(
			String url, String accessToken, String refreshToken) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (url != null) {
			Map<String, Object> response = makeGetRequestWithRefresh(url, accessToken, refreshToken);
			List<Map<String, Object>> pageResults = new ArrayList<Map<String, Object>>();
			Object rawCollection = response.get("collection");

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

	/**
     * Retrieves the authenticated user's SoundCloud profile.
     *
     * @param accessToken  Valid OAuth2 access token
     * @param refreshToken Valid OAuth2 refresh token for fallback
     * @return             Profile information as a {@code Map}
     */
	public Map<String, Object> getUserProfile(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequestWithRefresh(url, accessToken, refreshToken);
	}

	/**
     * Retrieves the user's liked tracks.
     * 
     * @return {@code List} of liked tracks
     */
	public List<Map<String, Object>> getUserLikes(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	/**
     * Retrieves the user's playlists.
     * 
     * @return {@code List} of playlists
     */
	public List<Map<String, Object>> getUserPlaylists(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + urlExtension("/me/playlists", 50);
		
		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	/**
     * Retrieves the user's followers.
     * 
     * @return {@code List} of followers
     */
	public List<Map<String, Object>> getUserFollowers(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + urlExtension("/me/followers", 50);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	/**
     * Retrieves the user's uploaded tracks.
     * 
     * @return {@code List} of tracks
     */
	public List<Map<String, Object>> getUserTracks(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + urlExtension("/me/tracks", 50);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	/**
     * Retrieves the user's liked tracks.
     * 
     * @return {@code List} of liked tracks
     */
	public List<Map<String, Object>> getRelatedTracks(
		String accessToken, String refreshToken, String trackUrn) {
		String url = soundCloudApiBaseUrl + urlExtension("/tracks/" + trackUrn + "/related", 10);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	/**
	 * Aggregates and calculates a user's full SoundCloud "Wrapped" statistics.
     * Returns raw summary data including profile info, top tracks, artists,
     * playlists, listening hours, reposts, and fun facts.
     *
     * This is a data-heavy method meant to be consumed internally
     * or reformatted by higher-level methods.
	 * 
	 * @param accessToken  Valid OAuth2 access token
	 * @param refreshToken Valid OAuth2 refresh token
	 * @return             Map containing unformatted Wrapped statistics
	 */
	public Map<String, Object> getFullWrappedSummary(String accessToken, String refreshToken) {
		Map<String, Object> wrapped = new HashMap<String, Object>();
		Map<String, Object> profile = getUserProfile(accessToken, refreshToken);
		List<Map<String, Object>> likes = getUserLikes(accessToken, refreshToken);
		List<Map<String, Object>> tracks = getUserTracks(accessToken, refreshToken);
		List<Map<String, Object>> playlists = getUserPlaylists(accessToken, refreshToken);
		List<Map<String, Object>> followers = getUserFollowers(accessToken, refreshToken);

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
     * @param accessToken  Valid OAuth2 access token
     * @param refreshToken Valid OAuth2 refresh token
     * @return             Map containing user-friendly Wrapped summary
     */
	public Map<String, Object> formattedWrappedSummary(String accessToken, String refreshToken) {
		Map<String, Object> raw = getFullWrappedSummary(accessToken, refreshToken);
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