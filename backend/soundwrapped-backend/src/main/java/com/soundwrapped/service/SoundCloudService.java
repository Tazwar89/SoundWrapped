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
		ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

		return response.getBody();
	}

	private Map<String, Object> makeGetRequestWithRefresh(String url, String accessToken, String refreshToken) {
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
	private List<Map<String, Object>> fetchPaginatedResultsWithRefresh(String initialUrl, String accessToken, String refreshToken) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (initialUrl != null) {
			Map<String, Object> response = makeGetRequestWithRefresh(initialUrl, accessToken, refreshToken);
			List<Map<String, Object>> pageResults = (List<Map<String, Object>>) response.get("collection");

			if (pageResults != null) {
				paginatedResults.addAll(pageResults);
			}

			initialUrl = (String) response.get("next_href");
		}

		return paginatedResults;
	}

	private int calculateAccountAgeYears(String createdAt) {
		//SoundCloud createdAt format example: "2013/03/23 14:58:27 +0000"
		SimpleDateFormat original = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
		Date parsedDate = null;

		try {
			parsedDate = original.parse(createdAt);
		}

		catch (ParseException pe) {
			return 0;
		}

		//Formatted convention for locale-based ZonedDateTime
		SimpleDateFormat formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		String formattedDate = formatted.format(parsedDate);
		ZonedDateTime createdDate = ZonedDateTime.parse((CharSequence) formattedDate);
		ZonedDateTime now = ZonedDateTime.now();

		return now.getYear() - createdDate.getYear();
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
		String url = soundCloudApiBaseUrl + "/me/playlists?linked_partitioning=true&limit=50";

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	public List<Map<String, Object>> getUserFollowers(String accessToken, String refreshToken) {
    	String url = soundCloudApiBaseUrl + "/me/followers?linked_partitioning=true&limit=50";

    	return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
    }

	public List<Map<String, Object>> getUserTracks(String accessToken, String refreshToken) {
		String url = soundCloudApiBaseUrl + "/me/tracks?linked_partitioning=true&limit=50";

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	public List<Map<String, Object>> getRelatedTracks(String accessToken, String refreshToken, String trackUrn) {
		String url = soundCloudApiBaseUrl + String.format("/tracks/{%s}/related?linked_partitioning=true&limit=10", trackUrn);

		return fetchPaginatedResultsWithRefresh(url, accessToken, refreshToken);
	}

	/**
	 * 
	 * @param accessToken an access token for user authentication
	 * @return            s
	 * @see #getUserLikes(String)
	 */
	public Map<String, Object> getWrappedLikes(String accessToken, String refreshToken) {
		List<Map<String, Object>> likes = getUserLikes(accessToken, refreshToken);
		Map<String, Integer> artistCounts = new HashMap<String, Integer>();
		Map<String, Long> artistListeningMs = new HashMap<String, Long>();
		Map<Integer, Integer> yearCounts = new HashMap<Integer, Integer>();
		List<Map<String, Object>> repostedTracks = new ArrayList<Map<String, Object>>();

		//Count top liked artists
		for (Map<String, Object> track : likes) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");

				if (artistName != null) {
					//Count likes per artist
					artistCounts.put(artistName, artistCounts.getOrDefault(artistName, 0) + 1);

					//Sum listening time for liked tracks
					long durationMs = ((Number) track.getOrDefault("duration", 0)).longValue();
					artistListeningMs.put(artistName, artistListeningMs.getOrDefault(artistName, 0L) + durationMs);
				}
			}

			Object createdAtObj = track.get("created_at");

			if (createdAtObj instanceof String createdAtStr) {
				SimpleDateFormat original = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
				Date parsedDate = null;

				try {
					parsedDate = original.parse(createdAtStr);
				}

				catch (ParseException pe) {
					System.out.println("Invalid date: " + createdAtStr);
				}

				//Formatted convention for locale-based ZonedDateTime
				SimpleDateFormat formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
				String formattedDate = formatted.format(parsedDate);
				ZonedDateTime createdDate = ZonedDateTime.parse((CharSequence) formattedDate);
				int year = createdDate.getYear();
				yearCounts.put(year, yearCounts.getOrDefault(year, 0) + 1);
			}
		}

		//Sort top 5 liked artists
		List<String> topArtistNames = artistCounts.entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();

		//Top 5 most reposted tracks
		List<Map<String, Object>> topRepostedTracks = repostedTracks.stream()
				.sorted((a, b) -> {
					return ((Integer) b.get("reposts_safe")).compareTo((Integer) a.get("reposts_safe"));
				}).limit(5).map(track -> Map.of("title", track
						.getOrDefault("title", "Unknown"), "reposts", track.get("reposts_safe"))).toList();

		//Convert artist listening times to hours
		Map<String, Double> artistListeningHours = new HashMap<String, Double>();

		for (Map.Entry<String, Long> entry : artistListeningMs.entrySet()) {
			double hours = entry.getValue() / 1000.0 / 60.0 / 60.0;
			artistListeningHours.put(entry.getKey(), hours);
		}

		//Top 5 artists by total listening hours
		List<String> topArtistsByHours = artistListeningHours.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();

		String peakYear = yearCounts.entrySet().stream().max(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey() + " (" + entry.getValue() + " likes)")
				.orElse("No data");

		Map<String, Object> wrappedData = new HashMap<String, Object>();
		wrappedData.put("topArtists", topArtistNames);
		wrappedData.put("totalLikes", likes.size());
		wrappedData.put("topRepostedTracks", topRepostedTracks);
		wrappedData.put("artistListeningHours", artistListeningHours);
		wrappedData.put("topArtistsbyHours", topArtistsByHours);
		wrappedData.put("peakYear", peakYear);

		return wrappedData;
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
				.sorted((a, b) -> Long.compare(((Number) b.getOrDefault("likes_count", 0))
						.longValue(), ((Number) a.getOrDefault("likes_count", 0))
						.longValue())).limit(5).toList();
		wrapped.put("topLikedPlaylists", topLikedPlaylists);

		Map<String, Integer> artistCounts = new HashMap<>();
		Map<String, Long> artistListeningMs = new HashMap<>();

		//Count likes per artist and accumulate track durations
		for (Map<String, Object> track : likes) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				artistCounts.put(artistName, artistCounts.getOrDefault(artistName, 0) + 1);
			}
		}

		for (Map<String, Object> track : tracks) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				long durationMs = ((Number) track.getOrDefault("duration", 0)).longValue();
				artistListeningMs.put(artistName, artistListeningMs.getOrDefault(artistName, 0L) + durationMs);
			}
		}

		//Top 5 most liked artists
		List<String> topLikedArtists = artistCounts.entrySet().stream().sorted((a, b) -> b
				.getValue().compareTo(a.getValue())).limit(5).map(Map.Entry::getKey).toList();
		wrapped.put("topLikedArtists", topLikedArtists);

		//Artist listening hours
		Map<String, Double> artistListeningHours = new HashMap<>();

		for (Map.Entry<String, Long> entry : artistListeningMs.entrySet()) {
			artistListeningHours.put(entry.getKey(), entry.getValue() / 1000.0 / 60.0 / 60.0);
		}

		wrapped.put("artistListeningHours", artistListeningHours);

		//Top 5 artists by listening hours
		List<String> topArtistsByHours = artistListeningHours.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();
		wrapped.put("topArtistsByHours", topArtistsByHours);

		//Global music taste placeholder
		wrapped.put("globalTasteComparison", "Listeners in New York, Berlin and Tokyo share your musical taste! 🎧");

		return wrapped;
    }
}