package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * SoundCloudService
 * 
 * @author Tazwar Sikder
 */

@Service
public class SoundCloudService {
	@Value("${soundcloud.api.base-url:https://api.soundcloud.com}")
	private String soundCloudApiBaseUrl;
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
		ResponseEntity<Map> trackData = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

		return trackData.getBody();
	}

	/**
	 * "Overloaded" version of makeGetRequest to send GET requests for endpoints
	 * returning paginated JSON arrays
	 * 
	 * @param url         the SoundCloud endpoint to call
	 * @param accessToken an access token for user authentication
	 * @return            JSON response body as a Map (for multiple tracks)
	 */
	private List<Map<String, Object>> fetchPaginatedResults(String initialUrl, String accessToken) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (initialUrl != null) {
			Map<String, Object> response = makeGetRequest(initialUrl, accessToken);
			List<Map<String, Object>> pageResults = (List<Map<String, Object>>) response.get("collection");

			if (paginatedResults != null) {
				paginatedResults.addAll(pageResults);
			}

			initialUrl = (String) response.get("next_href");
		}

		return paginatedResults;
	}

	public Map<String, Object> getUserProfile(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequest(url, accessToken);
	}

	public List<Map<String, Object>> getUserLikes(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserPlaylists(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/playlists?linked_partitioning=true&limit=50";

		return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserFollowers(String accessToken) {
    	String url = soundCloudApiBaseUrl + "/me/followers?linked_partitioning=true&limit=50";

    	return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserTracks(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/tracks?linked_partitioning=true&limit=50";

		return fetchPaginatedResults(url, accessToken);
	}

	public List<Map<String, Object>> getRelatedTracks(String accessToken, String trackUrn) {
		String url = soundCloudApiBaseUrl + String.format("/tracks/{%s}/related?linked_partitioning=true&limit=10", trackUrn);

		return fetchPaginatedResults(url, accessToken);
	}

	/**
	 * 
	 * @param accessToken an access token for user authentication
	 * @return            s
	 * @see #getUserLikes(String)
	 */
	public Map<String, Object> getWrappedLikes(String accessToken) {
		List<Map<String, Object>> likes = getUserLikes(accessToken);
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
		Map<String, Double> artistListeningHours = new HashMap<>();

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

		Map<String, Object> wrappedData = new HashMap<>();
		wrappedData.put("topArtists", topArtistNames);
		wrappedData.put("totalLikes", likes.size());
		wrappedData.put("topRepostedTracks", topRepostedTracks);
		wrappedData.put("artistListeningHours", artistListeningHours);
		wrappedData.put("topArtistsbyHours", topArtistsByHours);
		wrappedData.put("peakYear", peakYear);

		return wrappedData;
	}
}