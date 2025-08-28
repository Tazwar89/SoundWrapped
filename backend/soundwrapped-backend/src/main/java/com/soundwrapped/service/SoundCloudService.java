package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
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
		List<Map<String, Object>> tracks = getUserTracks(accessToken);
		Map<String, Integer> artistCounts = new HashMap<String, Integer>();
		Map<String, Long> artistListeningMs = new HashMap<String, Long>();

		//Count top liked artists
		for (Map<String, Object> track : likes) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				artistCounts.put(artistName, artistCounts.getOrDefault(artistName, 0) + 1);
			}
		}
		
		//Sum track durations per top liked artist
		for (Map<String, Object> track : tracks) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				long durationMs = ((Number) track.getOrDefault("duration", 0)).longValue();
				artistListeningMs.put(artistName, artistListeningMs
						.getOrDefault(artistName, 0L) + durationMs);
			}
		}

		//Sort top 5 liked artists
		List<String> topArtistNames = artistCounts.entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();

		//Top 5 most reposted tracks
		List<Map<String, Object>> topRepostedTracks = tracks.stream()
				.sorted((a, b) -> Long.compare(((Number) b.getOrDefault("reposts_count", 0))
				.longValue(), ((Number) a.getOrDefault("reposts_count", 0)).longValue()))
				.limit(5).toList();

		// Convert artist listening times to hours
		Map<String, Double> artistListeningHours = new HashMap<>();

		for (Map.Entry<String, Long> entry : artistListeningMs.entrySet()) {
			double hours = entry.getValue() / 1000.0 / 60.0 / 60.0;
			artistListeningHours.put(entry.getKey(), hours);
		}

		// Top 5 artists by total listening hours
		List<String> topArtistsByHours = artistListeningHours.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();

		Map<String, Object> wrappedData = new HashMap<>();
		wrappedData.put("topArtists", topArtistNames);
		wrappedData.put("totalLikes", likes.size());
		wrappedData.put("topRepostedTracks", topRepostedTracks);
		wrappedData.put("artistListeningHours", artistListeningHours);
		wrappedData.put("topArtistsbyHours", topArtistsByHours);

		return wrappedData;
	}	
}