package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
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
 * SoundCloudService handles all SoundCloud API calls and Wrapped summary aggregation.
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
	 * Sends HTTP GET requests (using Spring's RestTemplate utility) to the URL with
	 * user's authorization code, and returns response data
	 * 
	 * @param url         the SoundCloud endpoint to call
	 * @param accessToken an access token for user authentication
	 * @return            JSON response body as a Map (for a single track)
	 */
	private Map<String, Object> makeGetRequest(String url, String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		ResponseEntity<Map> response = restTemplate
				.exchange(url, HttpMethod.GET, entity, Map.class);

		return response.getBody();
	}

	private Map<String, Object> makeGetRequestWithRefresh(
			String url, String accessToken, String refreshToken) {
		try {
			return makeGetRequest(url, accessToken);
		}

		catch (HttpClientErrorException.Unauthorized htee) {
			// Access token expired → refresh it
			Map<String, Object> newTokens = refreshAccessToken(refreshToken);
			String newAccessToken = (String) newTokens.get("access_token");

			// Optionally update stored access token in DB here
			return makeGetRequest(url, newAccessToken);
		}
	}

	/**
	 * "Overloaded" version of makeGetRequest to send GET requests for endpoints
	 * returning paginated JSON arrays
	 * 
	 * @param url         the SoundCloud endpoint to call
	 * @param accessToken an access token for user authentication
	 * @return            JSON response body as a Map (for multiple tracks)
	 */
	private List<Map<String, Object>> fetchPaginatedResultsWithRefresh(
			String url, String accessToken, String refreshToken) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (url != null) {
			Map<String, Object> response = makeGetRequestWithRefresh(url, accessToken, refreshToken);
			List<Map<String, Object>> pageResults;
			pageResults = (List<Map<String, Object>>) response.get("collection");

			if (pageResults != null) {
				paginatedResults.addAll(pageResults);
			}

			url = (String) response.get("next_href");
		}

		return paginatedResults;
	}

	private String urlExtension(String prefix, int limit) {
		return String.format("%s?linked_partitioning=true&limit=%d", prefix, limit);
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
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artist = (String) user.get("username");

				if (artist != null) {
					artistCounts.put(artist, artistCounts.getOrDefault(artist, 0) + 1);
				}
			}	
		}

		return artistCounts;
	}

	private Map<String, Long> calculateArtistListeningMs(List<Map<String, Object>> tracks) {
		Map<String, Long> artistListeningMs = new HashMap<String, Long>();

		for (Map<String, Object> track : tracks) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artist = (String) user.get("username");
				long duration = ((Number) track.getOrDefault("duration", 0)).longValue();
				artistListeningMs.put(artist, artistListeningMs.getOrDefault(artist, 0L) + duration);
			}
		}

		return artistListeningMs;
	}

	// What does the ? do
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
	 * OAuth2 token refresh
	 * 
	 * @param refreshToken
	 * @return
	 */
	public Map<String, Object> refreshAccessToken(String refreshToken) {
		String url = "https://api.soundcloud.com/oauth2/token";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "refresh_token");
		body.add("refresh_token", refreshToken);
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
		ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

		return response.getBody();
	}

	public Map<String, Object> getUserProfile(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequestWithRefresh(url, accessToken, refreshToken);
	}

	public List<Map<String, Object>> getUserLikes(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	public List<Map<String, Object>> getUserPlaylists(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + urlExtension("/me/playlists", 50);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	public List<Map<String, Object>> getUserFollowers(String accessToken, String refreshToken) {
    	String url = soundCloudApiBaseUrl + urlExtension("/me/followers", 50);

    	return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	public List<Map<String, Object>> getUserTracks(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + urlExtension("/me/tracks", 50);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	public List<Map<String, Object>> getRelatedTracks(
		String accessToken, String refreshToken, String trackUrn) {
		String url = soundCloudApiBaseUrl + urlExtension("/tracks/" + trackUrn + "/related", 10);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	/**
	 * getWrappedSummary 
	 * 
	 * 
	 * List<Map<String, Object>> is used throughout this class because each element of
	 * the List contains metadata for individual tracks, i.e. each track has a Map
	 * including key-value information such as title: Song A, duration: 35000 sec, etc.
	 * 
	 * @param accessToken an access token for user authentication
	 * @return            a Map containing all essential Wrapped statistics
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
}